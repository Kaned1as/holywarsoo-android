package com.kanedias.holywarsoo.markdown

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.*
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.kanedias.holywarsoo.BuildConfig
import com.kanedias.holywarsoo.R
import com.kanedias.holywarsoo.databinding.ViewImageOverlayBinding
import com.kanedias.holywarsoo.misc.dpToPixel
import com.kanedias.holywarsoo.service.Network
import com.kanedias.holywarsoo.service.SpanCache
import com.kanedias.html2md.Html2Markdown
import com.stfalcon.imageviewer.StfalconImageViewer
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.BlockQuoteSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.HtmlTag
import io.noties.markwon.html.MarkwonHtmlRenderer
import io.noties.markwon.html.TagHandler
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.utils.NoCopySpannableFactory
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Get markdown setup from context.
 *
 * This is stripped-down version for use in notifications and other
 * cases where user interaction is not needed.
 *
 * @param ctx context to initialize from
 */
fun mdRendererFrom(ctx: Context): Markwon {
    return Markwon.builder(ctx)
        .usePlugin(object: AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.blockMargin(dpToPixel(16f, ctx).toInt())
            }
        })
        .usePlugin(HtmlPlugin.create()
            .addHandler(DetailsTagHandler()))
        .usePlugin(GlideImagesPlugin.create(GlideGifSupportStore(ctx)))
        .usePlugin(StrikethroughPlugin.create())
        .build()
}

fun mdThemeFrom(ctx: Context): MarkwonTheme {
    return MarkwonTheme.builderWithDefaults(ctx)
        .blockMargin(dpToPixel(16f, ctx).toInt())
        .build()
}

/**
 * Perform all necessary steps to view Markdown in this text view.
 * Parses input with html2md library and converts resulting markdown to spanned string.
 * @param md input markdown to show
 */
fun TextView.handleMarkdown(md: Spanned) {
    val label = this
    label.setSpannableFactory(NoCopySpannableFactory())

    label.setText(md, TextView.BufferType.SPANNABLE)


    // FIXME: see https://github.com/noties/Markwon/issues/120
    label.addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {

        }

        override fun onViewAttachedToWindow(v: View?) {
            AsyncDrawableScheduler.schedule(label)
        }

    })

    AsyncDrawableScheduler.schedule(label)
}

fun toMarkdown(msgId: Int, html: String, ctx: Context): Spanned {
    val converter = {
        val mdContent = Html2Markdown().parse(html)
        val spanned = mdRendererFrom(ctx).toMarkdown(mdContent) as SpannableStringBuilder
        postProcessSpans(spanned, ctx)

        spanned
    }

    return SpanCache.forMessageId(msgId, converter)
}

/**
 * Post-process spans like spoilers or image loading
 * @param spanned editable spannable to change
 * @param ctx context to resolve dimensions and strings
 */
fun postProcessSpans(spanned: SpannableStringBuilder, ctx: Context) {
    postProcessDrawables(spanned)
    postProcessMore(spanned, ctx)
}

/**
 * Post-process drawables, so you can click on them to see them in full-screen
 * @param spanned editable spannable to change
 */
fun postProcessDrawables(spanned: SpannableStringBuilder) {
    val imgSpans = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
    imgSpans.sortBy { spanned.getSpanStart(it) }

    for (img in imgSpans) {
        val start = spanned.getSpanStart(img)
        val end = spanned.getSpanEnd(img)
        val spansToWrap = spanned.getSpans(start, end, CharacterStyle::class.java)
        if (spansToWrap.any { it is ClickableSpan }) {
            // the image is already clickable, we can't replace it
            continue
        }

        val wrapperClick = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val index = imgSpans.indexOf(img)
                if (index == -1) {
                    // something modified spannable in a way image is no longer here
                    return
                }

                val overlay = ImageShowOverlay(widget.context)
                overlay.update(imgSpans[index])

                StfalconImageViewer.Builder(widget.context, imgSpans) { view, span ->
                    val resolved = Network.resolve(span.drawable.destination) ?: return@Builder
                    Glide.with(view).load(resolved.toString()).into(view)
                }
                    .withOverlayView(overlay)
                    .withStartPosition(index)
                    .withImageChangeListener { position -> overlay.update(imgSpans[position])}
                    .allowSwipeToDismiss(true)
                    .show()
            }
        }

        spanned.setSpan(wrapperClick, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/**
 * Post-process MORE statements in the text. They act like `<spoiler>` or `<cut>` tag in some websites
 * @param spanned text to be modified to cut out MORE tags and insert replacements instead of them
 * @param ctx context to resolve dimensions and strings
 */
fun postProcessMore(spanned: SpannableStringBuilder, ctx: Context) {
    val spans = spanned.getSpans(0, spanned.length, DetailsParsingSpan::class.java)
    spans.sortBy { spanned.getSpanStart(it) }

    // if we have no details, proceed as usual (single text-view)
    if (spans.isNullOrEmpty()) {
        // no details
        return
    }

    for (span in spans) {
        val startIdx = spanned.getSpanStart(span)
        val endIdx = spanned.getSpanEnd(span)

        var summaryStartIdx = spanned.getSpanStart(span.summary)
        var summaryEndIdx = spanned.getSpanEnd(span.summary)

        // details tags can be nested, skip them if they were hidden
        if (startIdx == -1 || endIdx == -1) {
            continue
        }

        // empty summary text or image as a summary text
        if (summaryEndIdx == -1 && summaryStartIdx == -1 && span.summary.text.isEmpty()) {
            summaryStartIdx = startIdx
            summaryEndIdx = startIdx
            span.summary.text = ctx.getString(R.string.more_tag_default)
        }

        // replace text inside spoiler tag with just spoiler summary that is clickable
        val summaryText = when (span.state) {
            DetailsSpanState.CLOSED -> "${span.summary.text} ▼\n\n"
            DetailsSpanState.OPENED  -> "${span.summary.text} ▲\n\n"
            else -> ""
        }

        when (span.state) {

            DetailsSpanState.CLOSED -> {
                span.state = DetailsSpanState.DORMANT
                spanned.removeSpan(span.summary) // will be added later

                // spoiler tag must be closed, all the content under it must be hidden

                // retrieve content under spoiler tag and hide it
                // if it is shown, it should be put in blockquote to distinguish it from text before and after
                val innerSpanned = spanned.subSequence(summaryEndIdx, endIdx) as SpannableStringBuilder
                spanned.replace(summaryStartIdx, endIdx, summaryText)
                spanned.setSpan(span.summary, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                // expand text on click
                val wrapper = object : ClickableSpan() {

                    // replace wrappers with real previous spans on click
                    @Suppress("NAME_SHADOWING")
                    override fun onClick(widget: View) {
                        span.state = DetailsSpanState.OPENED

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            // on devices with api < 28 span watcher update sometimes causes IndexOutOfBoundsException
                            // see https://issuetracker.google.com/issues/67924069
                            spanned.getSpans(0, spanned.length, SpanWatcher::class.java).forEach { spanned.removeSpan(it) }
                        }

                        val start = spanned.getSpanStart(this)
                        val end = spanned.getSpanEnd(this)

                        spanned.removeSpan(this)
                        spanned.insert(end, innerSpanned)

                        // make details span cover all expanded text
                        spanned.removeSpan(span)
                        spanned.setSpan(span, start, end + innerSpanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                        // edge-case: if the span around this text is now too short, expand it as well
                        spanned.getSpans(end, end, Any::class.java)
                            .filter { spanned.getSpanEnd(it) == end }
                            .forEach {
                                if (it is DetailsSummarySpan) {
                                    // don't expand summaries, they are meant to end there
                                    return@forEach
                                }

                                val bqStart = spanned.getSpanStart(it)
                                spanned.removeSpan(it)
                                spanned.setSpan(it, bqStart, end + innerSpanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }

                        postProcessMore(spanned, widget.context)

                        val textView = widget as TextView
                        textView.text = spanned
                        AsyncDrawableScheduler.schedule(textView)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = ds.linkColor
                    }
                }
                spanned.setSpan(wrapper, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            DetailsSpanState.OPENED -> {
                span.state = DetailsSpanState.DORMANT

                // put the hidden text into blockquote if needed
                var bq = spanned.getSpans(summaryEndIdx, endIdx, BlockQuoteSpan::class.java)
                    .firstOrNull { spanned.getSpanStart(it) == summaryEndIdx && spanned.getSpanEnd(it) == endIdx }
                if (bq == null) {
                    bq = BlockQuoteSpan(mdThemeFrom(ctx))
                    spanned.setSpan(bq, summaryEndIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                // content under spoiler tag is shown, but should be hidden again on click
                // change summary text to opened variant
                spanned.replace(summaryStartIdx, summaryEndIdx, summaryText)
                spanned.setSpan(span.summary, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val wrapper = object : ClickableSpan() {

                    // hide text again on click
                    override fun onClick(widget: View) {
                        span.state = DetailsSpanState.CLOSED

                        spanned.removeSpan(this)

                        postProcessMore(spanned, widget.context)

                        (widget as TextView).text = spanned
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = ds.linkColor
                    }
                }
                spanned.setSpan(wrapper, startIdx, startIdx + summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            DetailsSpanState.DORMANT -> {
                // this state is present so that details spans that were already processed won't be processed again
                // nothing should be done
            }
        }
    }
}

/**
 * Class responsible for showing "Share" and "Download" buttons when viewing images in full-screen.
 */
class ImageShowOverlay(ctx: Context,
                       attrs: AttributeSet? = null,
                       defStyleAttr: Int = 0) : FrameLayout(ctx, attrs, defStyleAttr) {

    private val binding = ViewImageOverlayBinding.inflate(LayoutInflater.from(ctx), this)

    fun update(span: AsyncDrawableSpan) {
        val resolved = Network.resolve(span.drawable.destination) ?: return

        // share button: share the image using file provider
        binding.overlayShare.setOnClickListener {
            Glide.with(it).asFile().load(resolved.toString()).into(object: SimpleTarget<File>() {

                override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                    val shareUri = saveToShared(resource)

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_image_using)))
                }

            })
        }

        // download button: download the image to Download folder on internal SD
        binding.overlayDownload.setOnClickListener {
            val activity = context as? Activity ?: return@setOnClickListener

            // request SD write permissions if we don't have it already
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                return@setOnClickListener
            }

            Toast.makeText(context, R.string.downloading_image, Toast.LENGTH_SHORT).show()
            Glide.with(it).asFile().load(resolved.toString()).into(object: SimpleTarget<File>() {

                override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                    val downloadDir = Environment.DIRECTORY_DOWNLOADS
                    val filename = resolved.pathSegments.last()

                    // on API >= 29 we must use media store API, direct access to SD-card is no longer available
                    val ostream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, downloadDir)
                        }
                        val imageUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)!!
                        resolver.openOutputStream(imageUri)!!
                    } else {
                        @Suppress("DEPRECATION") // should work on API < 29
                        val downloads = Environment.getExternalStoragePublicDirectory(downloadDir)
                        File(downloads, filename).outputStream()
                    }

                    ostream.write(resource.readBytes())

                    val report = context.getString(R.string.image_saved_as) + " $downloadDir/$filename"
                    Toast.makeText(context, report, Toast.LENGTH_SHORT).show()
                }

            })
        }
    }

    /**
     * Save image file to location that is shared from xml/shared_paths.
     * After that it's possible to share this file to other applications
     * using [FileProvider].
     *
     * @param image image file to save
     * @return Uri that file-provider returns, or null if unable to
     */
    private fun saveToShared(image: File) : Uri? {
        try {
            val sharedImgs = File(context.cacheDir, "shared_images")
            if (!sharedImgs.exists() && !sharedImgs.mkdir()) {
                Log.e("Fair/Markdown", "Couldn't create dir for shared imgs! Path: $sharedImgs")
                return null
            }

            // cleanup old images
            for (oldImg in sharedImgs.listFiles().orEmpty()) {
                if (!oldImg.delete()) {
                    Log.w("Fair/Markdown", "Couldn't delete old image file! Path $oldImg")
                }
            }

            val imgTmpFile = File(sharedImgs, UUID.randomUUID().toString())
            imgTmpFile.writeBytes(image.readBytes())

            return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", imgTmpFile)
        } catch (e: IOException) {
            Log.d("Fair/Markdown", "IOException while trying to write file for sharing", e)
        }

        return null
    }
}

/**
 * Custom tag handler that deals with `<details>` and `<summary>` tags. They are used to implement
 * spoilers in the text.
 *
 * This is highly specific to Visman's [modification](https://github.com/MioVisman/FluxBB_by_Visman) of FluxBB forum.
 */
class DetailsTagHandler: TagHandler() {

    override fun handle(visitor: MarkwonVisitor, renderer: MarkwonHtmlRenderer, tag: HtmlTag) {
        var summaryEnd = -1
        var summaryStart = -1
        for (child in tag.asBlock.children()) {

            if (!child.isClosed) {
                continue
            }

            if ("summary" == child.name()) {
                summaryStart = child.start()
                summaryEnd = child.end()
            }

            val tagHandler = renderer.tagHandler(child.name())
            if (tagHandler != null) {
                tagHandler.handle(visitor, renderer, child)
            } else if (child.isBlock) {
                visitChildren(visitor, renderer, child.asBlock)
            }
        }

        if (summaryEnd > -1 && summaryStart > -1) {
            val summaryText = visitor.builder().subSequence(summaryStart, summaryEnd)
            val summarySpan = DetailsSummarySpan(summaryText)
            visitor.builder().setSpan(summarySpan, summaryStart, summaryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            visitor.builder().setSpan(DetailsParsingSpan(summarySpan), tag.start(), tag.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun supportedTags(): Collection<String> {
        return Collections.singleton("details")
    }
}

data class DetailsSummarySpan(var text: CharSequence)

enum class DetailsSpanState { DORMANT, CLOSED, OPENED }

data class DetailsParsingSpan(
    val summary: DetailsSummarySpan,
    var state: DetailsSpanState = DetailsSpanState.CLOSED
)