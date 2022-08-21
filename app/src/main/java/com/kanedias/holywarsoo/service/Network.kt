package com.kanedias.holywarsoo.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.kanedias.holywarsoo.BuildConfig
import com.kanedias.holywarsoo.R
import com.kanedias.holywarsoo.dto.*
import com.kanedias.holywarsoo.markdown.toMarkdown
import com.kanedias.holywarsoo.misc.sanitizeInt
import com.kanedias.holywarsoo.misc.trySanitizeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * The singleton responsible for performing all network-related operations for the application,
 * such as:
 * * Logging in
 * * Retrieving forums, topics, messages
 * * Creating messages, topics
 * * Editing messages, topics
 * * Reporting messages
 * * Reporting errors of loading back
 *
 * All methods in here must be thread-safe.
 *
 * All methods here *may* throw [IOException]
 *
 * @author Kanedias
 *
 * Created on 2019-12-17
 */
@SuppressLint("StaticFieldLeak") // we pass an application context, its lifespan is same as object anyway
object Network {
    private const val COOKIES_SHARED_PREFS = "cookies"
    private const val ACCOUNT_SHARED_PREFS = "account"
    private const val PREF_USERNAME = "username"
    private const val PREF_PASSWORD = "password"

    private const val USER_AGENT = "Holywarsoo Android ${BuildConfig.VERSION_NAME}"
    private const val IMGUR_CLIENT_AUTH = "Client-ID ${BuildConfig.IMGUR_API_KEY}"
    private val MAIN_IMGUR_URL = "https://api.imgur.com".toHttpUrl()

    private lateinit var MAIN_WEBSITE_URL: HttpUrl
    lateinit var FAVORITE_TOPICS_URL: String
    lateinit var REPLIES_TOPICS_URL: String
    lateinit var NEW_MESSAGES_TOPICS_URL: String
    lateinit var RECENT_TOPICS_URL: String
    lateinit var SUBSCRIBED_TOPICS_URL: String
    lateinit var OWN_TOPICS_URL: String
    lateinit var OWN_MESSAGES_URL: String

    private val userAgent = Interceptor { chain ->
        chain.proceed(chain
            .request()
            .newBuilder()
            .header("User-Agent", USER_AGENT)
            .build())
    }

    private lateinit var appCtx: Context
    private lateinit var httpClient: OkHttpClient
    private lateinit var accountInfo: SharedPreferences
    private lateinit var cookiesInfo: SharedPreferences
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var cookiePersistor: SharedPrefsCookiePersistor
    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    fun init(ctx: Context) {
        appCtx = ctx

        prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != Config.HOME_URL)
                return@OnSharedPreferenceChangeListener

            // check validity of new homeserver
            val isValid = Config.homeUrl.toHttpUrlOrNull() != null
            if (!isValid) {
                Toast.makeText(appCtx, R.string.invalid_home_server_endpoint, Toast.LENGTH_LONG).show()
                Config.reset(appCtx)
                return@OnSharedPreferenceChangeListener
            }

            // url is valid
            setupEndpoints(Config.homeUrl)
        }

        Config.prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        setupEndpoints(Config.homeUrl)

        accountInfo = appCtx.getSharedPreferences(ACCOUNT_SHARED_PREFS, Context.MODE_PRIVATE)
        cookiesInfo = appCtx.getSharedPreferences(COOKIES_SHARED_PREFS, Context.MODE_PRIVATE)
        cookiePersistor = SharedPrefsCookiePersistor(cookiesInfo)
        cookieJar = PersistentCookieJar(SetCookieCache(), cookiePersistor)
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool())
            .dispatcher(Dispatcher())
            .addInterceptor(userAgent)
            .cookieJar(cookieJar)
            .build()
    }

    private fun setupEndpoints(url: String) {
        MAIN_WEBSITE_URL = url.toHttpUrl()
        FAVORITE_TOPICS_URL = resolve("search.php?action=show_favorites")!!.toString()
        REPLIES_TOPICS_URL = resolve("search.php?action=show_replies")!!.toString()
        NEW_MESSAGES_TOPICS_URL = resolve("search.php?action=show_new")!!.toString()
        RECENT_TOPICS_URL = resolve("search.php?action=show_recent")!!.toString()
        SUBSCRIBED_TOPICS_URL = resolve("search.php?action=show_subscriptions")!!.toString()
        OWN_TOPICS_URL = resolve("search.php?action=show_user_topics")!!.toString()
        OWN_MESSAGES_URL = resolve("search.php?action=show_user_posts")!!.toString()
    }

    private fun authCookie() = cookiePersistor.loadAll().firstOrNull { it.name.startsWith("pun_cookie") }

    fun resolve(url: String?): HttpUrl? {
        if (url.isNullOrEmpty())
            return null

        return MAIN_WEBSITE_URL.resolve(url)
    }

    fun daysToAuthExpiration() = authCookie()
        ?.let { (it.expiresAt - System.currentTimeMillis()) / 1000 / 60 / 60 / 24 }
        ?: 0L

    fun isLoggedIn() = cookiePersistor.loadAll()
        .filter { it.name.startsWith("pun_cookie") }
        .any { it.expiresAt > System.currentTimeMillis() }

    fun getUsername() = accountInfo.getString(PREF_USERNAME, null)

    /**
     * Only use if [isLoggedIn] returns true and username/password are populated
     * in preferences
     */
    fun refreshLogin() = login(
        accountInfo.getString(PREF_USERNAME, null)!!,
        accountInfo.getString(PREF_PASSWORD, null)!!
    )

    /**
     * Clears cookies in http client, clears saved username and password
     */
    fun logout() {
        cookieJar.clear()
        accountInfo.edit().clear().apply()
    }

    /**
     * Login using specified username and password.
     * Creates persistent cookies and account preferences.
     */
    @Throws(IOException::class)
    fun login(username: String, password: String) {
        // clear previous login information
        cookieJar.clear()

        // login page contains CSRF tokens and other form parameters that should be
        // present in the request for it to be valid. We need to extract this data prior to logging in
        val loginPageUrl = MAIN_WEBSITE_URL.resolve("login.php")!!
        val loginPageReq = Request.Builder().url(loginPageUrl).get().build()
        val loginPageResp = httpClient.newCall(loginPageReq).execute()
        if (!loginPageResp.isSuccessful)
            throw IllegalStateException("Can't load login page: ${loginPageResp.message}")

        val loginPageHtml = loginPageResp.body!!.string()
        val loginPageDoc = Jsoup.parse(loginPageHtml)

        val loginFormInputs =  loginPageDoc.select("form#login input[type=hidden]")

        val loginReqUrl = loginPageUrl.newBuilder()
            .addQueryParameter("action", "in")

        val reqBody = FormBody.Builder()
            .add("req_username", username)
            .add("req_password", password)
            .add("save_pass", "1")

        for (input in loginFormInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val loginReq = Request.Builder()
            .url(loginReqUrl.build())
            .post(reqBody.build())
            .build()

        val loginResp = httpClient.newCall(loginReq).execute()
        if (!loginResp.isSuccessful)
            throw IOException("Can't authenticate: ${loginResp.message}")

        if (authCookie() == null)
            throw IOException("Authentication failed, invalid login/password")

        accountInfo.edit()
            .putString(PREF_USERNAME, username)
            .putString(PREF_PASSWORD, password)
            .apply()
    }

    /**
     * Load forum list from the main page.
     * Forums on the main page also have category set.
     *
     * @return list of parsed forums. It has the same ordering as it had on the actual page.
     */
    @Throws(IOException::class)
    fun loadForumList(): List<ForumDesc> {
        val req = Request.Builder().url(MAIN_WEBSITE_URL).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load main page: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        val forumBoards = doc.select("div#brdmain > div.blocktable")
        val forums = mutableListOf<ForumDesc>()
        for (board in forumBoards) {
            val category = board.select("h2 > span").first().text()
            forums.addAll(parseForums(board, category))
        }

        return forums
    }

    /**
     * Loads quote for specified [topicId] from the [messageId].
     *
     * Both arguments must be present in order to get the quote,
     * but the [topicId] can be any valid topic id of your choosing.
     * It is required mainly because that's how forum API looks like.
     *
     *
     * @param topicId topic id the message is intended to be quoted for
     * @param messageId identifier of the message to be quoted
     */
    @Throws(IOException::class)
    fun loadQuote(topicId: Int, messageId: Int): String {
        val replyWithQuoteUrl = resolve("post.php?tid=${topicId}&qid=${messageId}")!!

        val req = Request.Builder().url(replyWithQuoteUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load message quote: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        return doc.select("form#post textarea[name=req_message]").text()
    }

    /**
     * Loads search page completely, with all the supporting browsing info, caption, pages and topics.
     *
     * @param searchLink canonical link to the the search page, in form of `https://<website>/search.php?action=<action>`
     * @param page page number that is used in conjunction with [searchLink] to produce paged link
     *
     * @return fully enriched search results instance.
     *         Topics have the same ordering as they had on the actual page.
     */
    @Throws(IOException::class)
    fun loadSearchTopicResults(searchLink: String, page: Int = 1): SearchResults<ForumTopicDesc> {
        val pageUrl = searchLink.toHttpUrl().newBuilder().addQueryParameter("p", page.toString()).build()

        val req = Request.Builder().url(pageUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load forum contents: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        val pageName = doc.select("head title").text()

        val topics = parseTopics(doc, true)
        if (topics.isEmpty()) {
            // no topics at all
            return SearchResults(link = searchLink, name = pageName,
                pageCount = 1, currentPage = 1, results = topics)
        }

        val searchPageName = doc.select("div#brdmain > div.linkst ul.crumbs > li:last-child strong").text()
        val pageLinks = doc.select("div#brdmain > div.linkst p.pagelink")
        val currentPage = pageLinks.select("strong").text()
        val pageCount = pageLinks.first().children()
            .mapNotNull { it.ownText().trySanitizeInt() }
            .maxOrNull() ?: 0

        return SearchResults(
            link = searchLink,
            name = searchPageName,
            pageCount = pageCount,
            currentPage = currentPage.sanitizeInt(),
            results = topics
        )
    }

    /**
     * Loads search messages page completely, with messages content and permanent links to forums, topics and messages.
     * This function differs from [loadSearchTopicResults] as it returns not the topics but messages that matched
     * the [searchKeyword].
     *
     * @param searchLink canonical link to the the search page, in form of `https://<website>/search.php?action=<action>`
     * @param searchKeyword keyword to use with [searchLink] to know what to search for, exactly
     * @param page page number that is used in conjunction with [searchLink] to produce paged link
     *
     * @return fully enriched search results instance.
     *         Messages have the same ordering as they had on the actual page.
     */
    @Throws(IOException::class)
    fun loadSearchMessagesResults(searchLink: String? = null, searchKeyword: String? = null, page: Int = 1): SearchResults<ForumMessage> {
        val pageUrl = searchLink?.let { it.toHttpUrl().newBuilder()
                .addQueryParameter("sort_dir", "DESC")
                .addQueryParameter("p", page.toString())
                .build() }
            ?: searchKeyword?.let { resolve("search.php")!!.newBuilder()
                .addQueryParameter("action", "search")
                .addQueryParameter("keywords", it)
                .addQueryParameter("sort_dir", "DESC")
                .addQueryParameter("p", page.toString())
                .build() }
            ?: throw IllegalStateException("Both search link and keyword are null!")

        val req = Request.Builder().url(pageUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load search page contents: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        val pageName = doc.select("head title").text()

        val messages = parseMessages(doc)
        if (messages.isEmpty()) {
            // no messages at all
            return SearchResults(link = pageUrl.toString(), name = pageName,
                pageCount = 1, currentPage = 1, results = messages)
        }

        val searchPageName = doc.select("div#brdmain > div.linkst ul.crumbs > li:last-child strong").text()
        val pageLinks = doc.select("div#brdmain > div.linkst p.pagelink")
        val currentPage = pageLinks.select("strong").text()
        val pageCount = pageLinks.first().children()
            .mapNotNull { it.ownText().trySanitizeInt() }
            .maxOrNull() ?: 0

        return SearchResults(
            link = pageUrl.toString(),
            name = searchPageName,
            pageCount = pageCount,
            currentPage = currentPage.sanitizeInt(),
            results = messages
        )
    }

    /**
     * Loads forum page completely, with all the supporting browsing info, pages, subforums and topics.
     * Either [forumLink] or [customLink] **must** be present for this call.
     *
     * @param forumLink canonical link to the the forum page, in form of `https://<website>/viewforum.php?id=<forum-id>`
     * @param customLink any non-canonical link that leads to the forum page. Overrides [forumLink] if both are present
     * @param page page number that is used in conjunction with [forumLink] to produce paged link
     *
     * @return fully enriched forum instance.
     *         Topics/subforums have the same ordering as they had on the actual page.
     */
    @Throws(IOException::class)
    fun loadForumContents(forumLink: String? = null, customLink: String? = null, page: Int = 1): Forum {
        val pageUrl = customLink?.toHttpUrl()
            ?: forumLink?.toHttpUrl()?.newBuilder()?.addQueryParameter("p", page.toString())?.build()
            ?: throw IllegalStateException("Both forum link and custom link are null!")

        val req = Request.Builder().url(pageUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load forum contents: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        val subforums = parseForums(doc.select("div.subforumlist").first())
        val topics = parseTopics(doc, false)

        val forumRef = doc.select("head link[rel=canonical]").attr("href")
        val forumId = resolve(forumRef)?.queryParameter("id")
            ?: throw IOException("Invalid forum page: $forumRef")

        val forumName = doc.select("head title").text()
        val forumWritable = doc.select("div#brdmain div.linksb p.postlink a[href^=post.php]")

        val pageLinks = doc.select("div#brdmain > div.linkst p.pagelink").first()
            ?: throw IOException("Invalid forum page: $forumRef")

        val currentPage = pageLinks.select("strong").text()
        val pageCount = pageLinks.children()
            .mapNotNull { it.ownText().trySanitizeInt() }
            .maxOrNull() ?: 0

        return Forum(
            id = forumId.sanitizeInt(),
            name = forumName,
            link = resolve(forumRef)!!.toString(),
            isWritable = forumWritable.isNotEmpty(),
            pageCount = pageCount,
            currentPage = currentPage.sanitizeInt(),
            subforums = subforums,
            topics = topics
        )
    }

    /**
     * Loads topic page completely, with all the supporting browsing info, pages, messages and their content.
     * Either [topicLink] or [customLink] **must** be present for this call.
     *
     * @param topicLink canonical link to the the forum page, in form of `https://<website>/viewtopic.php?id=<forum-id>`
     * @param customLink any non-canonical link that leads to the forum page. Overrides [topicLink] if both are present
     * @param page page number that is used in conjunction with [topicLink] to produce paged link
     *
     * @return fully enriched topic instance. Messages have the same ordering as they had on the actual page.
     */
    @Throws(IOException::class)
    fun loadTopicContents(topicLink: String? = null, customLink: String? = null, page: Int = 1): ForumTopic {
        val pageUrl = customLink?.toHttpUrl()
            ?: topicLink?.toHttpUrl()?.newBuilder()?.addQueryParameter("p", page.toString())?.build()
            ?: throw IllegalStateException("Both forum link and custom link are null!")

        val req = Request.Builder().url(pageUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load topic contents: ${resp.message}")

        val html = resp.body!!.string()
        val doc = Jsoup.parse(html)

        // topic link is in the page header, topic id can be derived from it
        // topic name is page title and writable bit is derived from the presence of answer button
        val topicRef = doc.select("head link[rel=canonical]").attr("href")
        val topicId = resolve(topicRef)?.queryParameter("id")
            ?: throw IOException("Invalid topic page: $topicRef")

        val topicName = doc.select("head title").text()
        val topicWritable = doc.select("div#brdmain div.postlinksb p.postlink a[href^=post.php]")
        val topicFavorite = doc.select("div#brdmain div.postlinksb p.subscribelink1 a[href*=favorite]")
        val topicSubscribe = doc.select("div#brdmain div.postlinksb p.subscribelink1 a[href*=subscribe]")

        val pageLinks = doc.select("div#brdmain > div.linkst p.pagelink").first()
            ?: throw IOException("Invalid topic page: $topicRef")

        val currentPage = pageLinks.select("strong").text()
        val pageCount = pageLinks.children()
            .mapNotNull { it.ownText().trySanitizeInt() }
            .maxOrNull() ?: 0

        // delete action from the switchable links so they can be reused in any context
        val topicFavoriteLink = resolve(topicFavorite.attr("href"))?.newBuilder()
            ?.removeAllQueryParameters("action")?.build()?.toString()
        val topicSubscribeLink = resolve(topicSubscribe.attr("href"))?.newBuilder()
            ?.removeAllQueryParameters("action")?.build()?.toString()

        val messages = parseMessages(doc)

        return ForumTopic(
            id = topicId.sanitizeInt(),
            name = topicName,
            link = resolve(topicRef)!!.toString(),
            refererLink = resp.request.url.toString(),
            isWritable = topicWritable.isNotEmpty(),
            isFavorite = topicFavorite.attr("href").contains("unfavorite"),
            favoriteLink = topicFavoriteLink,
            isSubscribed = topicSubscribe.attr("href").contains("unsubscribe"),
            subscriptionLink = topicSubscribeLink,
            pageCount = pageCount,
            currentPage = currentPage.sanitizeInt(),
            messages = messages
        )
    }

    /**
     * Helper class that contains message contents.
     *
     * If the message being edited is a starting post of the topic,
     * this will also contain non-null subject of this topic.
     *
     * Polls and other options are currently not implemented.
     */
    data class EditMessageDesc(
        val subject: String? = null,
        val content: String
    )

    /**
     * Loads message contents that should be edited. We can't take text of the message as-is as it
     * contains various forum-specific BB-codes that we can't replicate precisely.
     *
     * @param messageId unique message identifier that points to message which needs editing
     * @return helper class containing current message info
     */
    @Throws(IOException::class)
    fun loadEditPost(messageId: Int): EditMessageDesc {
        val editUrl = resolve("edit.php")!!.newBuilder().addQueryParameter("id", messageId.toString()).build()
        val req = Request.Builder().url(editUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load message edit page: ${resp.message}")

        val editPageHtml = resp.body!!.string()
        val editPageDoc = Jsoup.parse(editPageHtml)

        val editSubject = editPageDoc.select("form#edit textarea[name=req_subject]").text()
        val editContent = editPageDoc.select("form#edit textarea[name=req_message]").text()

        return EditMessageDesc(editSubject, editContent)
    }

    /**
     * Updates the message with specified [messageId] to contain info from [messageDesc].
     * @param messageId unique message identifier that points to message which needs editing
     * @param messageDesc helper class containing info to be edited
     * @return redirection link that forum returned in response
     */
    @Throws(IOException::class)
    fun editMessage(messageId: Int, messageDesc: EditMessageDesc): HttpUrl {
        val editUrl = resolve("edit.php")!!.newBuilder().addQueryParameter("id", messageId.toString()).build()

        val req = Request.Builder().url(editUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load message edit page: ${resp.message}")

        val editPageHtml = resp.body!!.string()
        val editPageDoc = Jsoup.parse(editPageHtml)

        val editPageInputs = editPageDoc.select("form#edit input[type=hidden]")

        val reqBody = FormBody.Builder()
            .add("req_message", messageDesc.content)

        if (!messageDesc.subject.isNullOrEmpty()) {
            reqBody.add("req_subject", messageDesc.subject)
        }

        for (input in editPageInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val editMessageReq = Request.Builder()
            .url(editUrl.newBuilder().addQueryParameter("action", "edit").build())
            .post(reqBody.build())
            .build()

        // if we send edit too quickly website decides we are robots, need to wait a bit
        Thread.sleep(2000)

        val editMessageResp = httpClient.newCall(editMessageReq).execute()
        if (!editMessageResp.isSuccessful)
            throw IOException("Unexpected failure")

        // this is a redirect link page, such as "Message saved, please wait to be redirected"
        val editMessageHtml = editMessageResp.body!!.string()
        val editMessageDoc = Jsoup.parse(editMessageHtml)

        // we need to extract link from it
        val link = editMessageDoc.select("div#brdmain div.box a").attr("href")
        return resolve(link)!!
    }

    /**
     * Deletes message with the specified unique message identifier.
     *
     * @param messageId message identifier
     * @return redirection link that forum returned in response
     */
    @Throws(IOException::class)
    fun deleteMessage(messageId: Int): HttpUrl {
        val deleteUrl = resolve("delete.php")!!.newBuilder().addQueryParameter("id", messageId.toString()).build()

        val req = Request.Builder().url(deleteUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load message edit page: ${resp.message}")

        val deletePageHtml = resp.body!!.string()
        val deletePageDoc = Jsoup.parse(deletePageHtml)

        val deletePageInputs = deletePageDoc.select("form[action^=delete] input")

        val reqBody = FormBody.Builder()
        for (input in deletePageInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val deleteMessageReq = Request.Builder()
            .url(deleteUrl)
            .post(reqBody.build())
            .build()

        // if we send edit too quickly website decides we are robots, need to wait a bit
        Thread.sleep(2000)

        val deleteMessageResp = httpClient.newCall(deleteMessageReq).execute()
        if (!deleteMessageResp.isSuccessful)
            throw IOException("Unexpected failure")

        // this is a redirect link page, such as "Message saved, please wait to be redirected"
        val deleteMessageHtml = deleteMessageResp.body!!.string()
        val deleteMessageDoc = Jsoup.parse(deleteMessageHtml)

        // we need to extract link from it
        val link = deleteMessageDoc.select("div#brdmain div.box a").attr("href")
        return resolve(link)!!
    }

    @Throws(IOException::class)
    fun postMessage(topicId: Int, message: String): HttpUrl {
        val postUrl = resolve("post.php")!!.newBuilder().addQueryParameter("tid", topicId.toString()).build()

        val req = Request.Builder().url(postUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load topic reply page: ${resp.message}")

        val replyPageHtml = resp.body!!.string()
        val replyPageDoc = Jsoup.parse(replyPageHtml)

        val replyPageInputs = replyPageDoc.select("form#post input[type=hidden]")

        val reqBody = FormBody.Builder()
            .add("req_message", message)
            .add("favorite", "1")

        for (input in replyPageInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val postMessageReq = Request.Builder()
            .url(postUrl)
            .post(reqBody.build())
            .build()

        // if we send reply too quickly website decides we are robots, need to wait a bit
        Thread.sleep(2000)

        val postMessageResp = httpClient.newCall(postMessageReq).execute()
        if (!postMessageResp.isSuccessful)
            throw IOException("Unexpected failure")

        // this is a redirect link page, such as "Message saved, please wait to be redirected"
        val postMessageHtml = postMessageResp.body!!.string()
        val postMessageDoc = Jsoup.parse(postMessageHtml)

        // we need to extract link from it
        val link = postMessageDoc.select("div#brdmain div.box a").attr("href")
        return resolve(link)!!
    }

    @Throws(IOException::class)
    fun postTopic(forumId: Int, subject: String, message: String): HttpUrl {
        val postUrl = resolve("post.php")!!.newBuilder().addQueryParameter("fid", forumId.toString()).build()

        val req = Request.Builder().url(postUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load topic create page: ${resp.message}")

        val postPageHtml = resp.body!!.string()
        val postPageDoc = Jsoup.parse(postPageHtml)

        val postPageInputs = postPageDoc.select("form#post input[type=hidden]")

        val reqBody = FormBody.Builder()
            .add("req_subject", subject)
            .add("req_message", message)

        for (input in postPageInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val postTopicReq = Request.Builder()
            .url(postUrl)
            .post(reqBody.build())
            .build()

        // if we send reply too quickly website decides we are robots, need to wait a bit
        Thread.sleep(2000)

        val postTopicResp = httpClient.newCall(postTopicReq).execute()
        if (!postTopicResp.isSuccessful)
            throw IOException("Unexpected failure")

        // this is a redirect link page, such as "Message saved, please wait to be redirected"
        val postTopicHtml = postTopicResp.body!!.string()
        val postTopicDoc = Jsoup.parse(postTopicHtml)

        // we need to extract link from it
        val link = postTopicDoc.select("div#brdmain div.box a").attr("href")
        return resolve(link)!!
    }

    @Throws(IOException::class)
    fun postReport(messageId: Int, reason: String) {
        val postUrl = resolve("misc.php")!!.newBuilder().addQueryParameter("report", messageId.toString()).build()

        val req = Request.Builder().url(postUrl).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't load report message page: ${resp.message}")

        val postPageHtml = resp.body!!.string()
        val postPageDoc = Jsoup.parse(postPageHtml)

        val postPageInputs = postPageDoc.select("form#report input[type=hidden]")

        val reqBody = FormBody.Builder()
            .add("req_reason", reason)

        for (input in postPageInputs) {
            reqBody.add(input.attr("name"), input.attr("value"))
        }

        val reportReq = Request.Builder()
            .url(postUrl)
            .post(reqBody.build())
            .build()

        // if we send reply too quickly website decides we are robots, need to wait a bit
        Thread.sleep(2000)

        val reportResp = httpClient.newCall(reportReq).execute()
        if (!reportResp.isSuccessful)
            throw IOException("Unexpected failure")
    }

    /**
     * Manage favorites for currently logged in user. Using this when [isLoggedIn] is false
     * is undefined behaviour.
     *
     * @param topic topic to add or remove from favorites
     * @param action what to do. Possible values: `favorite`, `unfavorite`, `subscribe`, `unsubscribe`
     */
    fun manageFavorites(topic: ForumTopic, action: String = "favorite") {
        val url = resolve("misc.php")!!.newBuilder()
            .addQueryParameter("action", action)
            .addQueryParameter("tid", topic.id.toString())
            .build()

        val req = Request.Builder().url(url).get().build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't $action: ${resp.message}")
    }

    /**
     * Uploads image to Imgur provider.
     * See [Imgur API](https://api.imgur.com/endpoints/image) for more info.
     *
     * @param imageBytes byte array containing image bytes
     * @return string representing full link to uploaded image
     */
    fun uploadImage(imageBytes: ByteArray): String {
        val url = MAIN_IMGUR_URL.resolve("/3/image")!!

        val uploadForm = imageBytes.toRequestBody("image/*".toMediaType())
        val reqBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "file")
            .addFormDataPart("image", "android-holywarsoo-${UUID.randomUUID()}", uploadForm)

        val req = Request.Builder()
            .url(url)
            .header("Authorization", IMGUR_CLIENT_AUTH)
            .post(reqBody.build())
            .build()

        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful)
            throw IOException("Can't upload image: ${resp.message}")

        val respJson = JSONObject(resp.body!!.string())
        return respJson.getJSONObject("data").getString("link")
    }

    /**
     * Parses message list page of the topic and creates a structured list of all messages on the page.
     *
     * @param doc fully parsed document containing page with message list (topic)
     * @return list of parsed messages. It has the same ordering as it had on the actual page.
     */
    private fun parseMessages(doc: Document) = runBlocking {
        doc.select("div#brdmain div.blockpost")
            .map { message ->
                // detach message from the document, so it won't touch parent DOM concurrently
                message.remove()

                // extended info (present on the search page)
                val navLinks = NavLinksMap()
                val navBreadcrumbs = message.select("h2 > span > span > a")
                if (navBreadcrumbs.size == 3) {
                    // it's a search page, populate links
                    navLinks[NavigationScope.FORUM] = Pair(
                        navBreadcrumbs[0].text(),
                        resolve(navBreadcrumbs[0].attr("href"))!!.toString()
                    )
                    navLinks[NavigationScope.TOPIC] = Pair(
                        navBreadcrumbs[1].text(),
                        resolve(navBreadcrumbs[1].attr("href"))!!.toString())
                    navLinks[NavigationScope.MESSAGE] = Pair(
                        navBreadcrumbs[2].text(),
                        resolve(navBreadcrumbs[2].attr("href"))!!.toString())
                }


                // all message info is self-contained in the message box
                val msgDateLink = message.select("h2 > span a").last()
                val msgIndex = message.select("h2 > span > span.conr").text()
                val msgAuthor = message.select("div.postleft > dl > dt > strong:last-child").text()
                val msgAuthorAvatar = message.select("div.postleft dd.postavatar > img")
                val msgBody = message.select("div.postright > div.postmsg").first()
                val msgDate = msgDateLink.text()
                val msgEditable = message.select("div.postfoot li.postedit")
                val msgDeletable = message.select("div.postfoot li.postdelete")

                val msgUrl = resolve(msgDateLink.attr("href"))!!
                val msgAvatarUrl = resolve(msgAuthorAvatar.attr("src"))
                val msgId = msgUrl.queryParameter("pid")!!.toInt()

                async(Dispatchers.IO) {
                    ForumMessage(
                        id = msgId,
                        link = msgUrl.toString(),
                        navigationLinks = navLinks,
                        index = msgIndex.replace("#", "").toInt(),
                        author = msgAuthor,
                        isEditable = msgEditable.isNotEmpty(),
                        isDeletable = msgDeletable.isNotEmpty(),
                        authorAvatarUrl = msgAvatarUrl?.toString(),
                        createdDate = msgDate,
                        content = postProcessMessage(msgId, msgBody)
                    )
                }
            }.map { it.await() }
            .toList()
    }

    /**
     * Post-processes the message, replaces all JS-based spoiler tags with standard HTML
     * `<details>` + `<summary>` tags that were meant to be actual spoilers, without requiring JS.
     *
     * Also replaces all CSS-based `<span class="bss">` strikethrough tags with standard HTML
     * `<s>` tags, without requiring stylesheets.
     *
     * @param msgId unique message identifier. Used to locate cached content
     * @param msgBody element representing message body, usually `div.postmsg` in the document
     * @return HTML string after postprocessing
     */
    private fun postProcessMessage(msgId: Int, msgBody: Element): Spanned {
        // need to detect all expandable spoiler tags and replace them with
        // standard `details` + `summary`
        for (spoilerContainer in msgBody.select("div.hh_spoiler_box")) {
            spoilerContainer.apply {
                tagName("details") // replace quotebox `div` with `details` tag
                clearAttributes()          // clear all attributes it might have
            }

            val spoilerText = spoilerContainer.select("span.hh_spoiler_arrow + span").first()
            spoilerText.apply {
                tagName("summary") // replace spoiler text `div` with `summary` tag
                clearAttributes()          // clear all attributes it might have
                html(this.ownText())       // we don't need a span with down/up arrow, just spoiler text
                // select("span").remove() // remove ▼ symbol
            }
        }

        // need to replace all custom span strikethrough tags with
        // standard `s`
        for (strikeTag in msgBody.select("span.bbs")) {
            strikeTag.apply {
                tagName("s")
                clearAttributes()
            }
        }

        // other post-processing steps may follow, leaving this vacant...

        return toMarkdown(msgId, msgBody.outerHtml(), appCtx)
    }

    /**
     * Parses topic list page of the forum and creates a structured list of all topics on the page.
     *
     * @param doc fully parsed document containing page with topic list (forum/favorites/active etc.)
     * @return list of parsed forum topics. It has the same ordering as it had on the actual page.
     */
    private fun parseTopics(doc: Document, hasForumColumn: Boolean = false): List<ForumTopicDesc> {
        val (repliesClass, viewsClass) = when (hasForumColumn) {
            true -> Pair("tc3", null)
            false -> Pair("tc2", "tc3")
        }

        val topics = mutableListOf<ForumTopicDesc>()
        for (topic in doc.select("div#vf div.inbox table tr[class^=row]")) {
            val topicLink = topic.select("td.tcl > div.tclcon a").first() ?: continue

            // topic list page may have different layouts depending on whether it's search page
            // or forum page, so we should be smart about it, detecting row meanings by their names
            val isSticky = topic.classNames().contains("isticky")
            val isClosed = topic.classNames().contains("iclosed")
            val topicPageCount = topic.select("td.tcl span.pagestext a:last-child").text()

            val topicReplies = repliesClass.let { topic.select("td.${it}").text() }
            val topicViews = viewsClass?.let { topic.select("td.${it}").text() }

            val lastMessageLink = topic.select("td.tcr > a").first()
            val newMessageLink = topic.select("td.tcl span.newtext a")

            val topicUrl = resolve(topicLink.attr("href"))!!
            val lastMessageUrl = resolve(lastMessageLink?.attr("href"))
            val newMessageUrl = resolve(newMessageLink.attr("href"))

            topics.add(
                ForumTopicDesc(
                    sticky = isSticky,
                    closed = isClosed,
                    name = topicLink.text(),
                    url = topicUrl.toString(),
                    replyCount = topicReplies.trySanitizeInt(),
                    viewCount = topicViews.trySanitizeInt(),
                    pageCount = topicPageCount.toIntOrNull() ?: 1,
                    lastMessageUrl = lastMessageUrl?.toString(),
                    lastMessageDate = lastMessageLink?.text(),
                    newMessageUrl = newMessageUrl?.toString()
                )
            )
        }

        return topics
    }

    /**
     * Parses forum list page of the site and creates a structured list of
     * all forums encountered on the page.
     *
     * @param where element containing forum list (main or forum with subforums)
     * @param predefinedCategory optional category to set for items found
     * @return list of parsed forums. It has the same ordering as it had on the actual page.
     */
    private fun parseForums(where: Element?, predefinedCategory: String? = null): List<ForumDesc> {
        if (where == null) {
            return emptyList()

        }
        val forums = mutableListOf<ForumDesc>()
        for (forum in where.select("div.inbox > table > tbody > tr[class^=row]")) {
            // forums can be found in main page and in forum page as well, as a subforums
            // all the info is fortunately self-contained and same across all kinds of pages
            val forumLink = forum.select("td.tcl div > h3 > a")
            val forumSub = forum.select("td.tcl div.forumdesc")
            val forumTopics = forum.select("td.tc2").text()
            val forumMessages = forum.select("td.tc3").text()
            val lastMessageLink = forum.select("td.tcr > span > a")
            val lastMessageDate = forum.select("td.tcr > span > span")

            val forumUrl = resolve(forumLink.attr("href"))
            val lastMessageUrl = resolve(lastMessageLink.attr("href"))

            forums.add(
                ForumDesc(
                    name = forumLink.text(),
                    link = forumUrl.toString(),
                    subtext = forumSub.text(),
                    category = predefinedCategory,
                    lastMessageName = lastMessageLink.text(),
                    lastMessageLink = lastMessageUrl.toString(),
                    lastMessageDate = lastMessageDate.text(),
                    topicCount = forumTopics.sanitizeInt(),
                    messageCount = forumMessages.sanitizeInt()
                )
            )
        }
        return forums
    }

    /**
     * Handle typical network-related problems.
     * @param ctx context to get error string from
     * @param ex exception that should be reported
     */
    fun reportErrors(ctx: Context?, ex: Exception) {
        if (ctx == null) {
            // trying to report on closed fragment?
            return
        }

        when (ex) {
            // generic connection-level error, show as-is
            is IOException -> {
                Log.w("Fair/Network", "Connection error", ex)
                val errorText = ctx.getString(R.string.error_connecting)
                Toast.makeText(ctx, "$errorText: ${ex.message}", Toast.LENGTH_SHORT).show()
            }

            else -> throw ex
        }
    }

    /**
     * Helper function to avoid long `try { ... } catch(...) { report }` blocks in code.
     *
     * @param networkAction action to be performed in background thread
     * @param uiAction action to be performed after [networkAction], in UI thread
     */
    suspend fun <T> perform(
        networkAction: () -> T,
        uiAction: (input: T) -> Unit = {},
        exceptionAction: (ex: Exception) -> Unit = { ex -> reportErrors(appCtx, ex) }) {
        try {
            val result = withContext(Dispatchers.IO) { networkAction() }
            uiAction(result)
        } catch (ex: Exception) {
            exceptionAction(ex)
        }
    }

    /**
     * To English readers: you are not expected to understand this
     *
     * Пасхалка для пользователей holywarsoo.net ;)
     */
    @Suppress("FunctionName", "unused", "NonAsciiCharacters")
    private fun украсть_пароли_с_холиварки_без_регистрации_и_смс() {
        // это мы, опилки
    }

    /**
     * Русскоязычным пользователям: вам необязательно это понимать
     *
     * Easter egg for bbs.archlinux.org users ;)
     */
    @Suppress("FunctionName", "unused")
    private fun btw_i_use_arch() {
        // to my, trociny
    }
}