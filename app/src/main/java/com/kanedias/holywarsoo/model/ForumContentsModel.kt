package com.kanedias.holywarsoo.model

import androidx.lifecycle.MutableLiveData
import com.kanedias.holywarsoo.ForumContentFragment
import com.kanedias.holywarsoo.dto.Forum


/**
 * View model for the forum contents.
 *
 * @see ForumContentFragment
 *
 * @author Kanedias
 *
 * Created on 2019-12-21
 */
class ForumContentsModel : PageableModel() {

    /**
     * Forum that is being loaded
     */
    val forum = MutableLiveData<Forum>()

    /**
     * Indicate if content was loaded at minimum once
     */
    val refreshed = MutableLiveData<Boolean>()
}