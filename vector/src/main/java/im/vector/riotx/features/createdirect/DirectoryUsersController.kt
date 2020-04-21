/*
 *
 *  * Copyright 2019 New Vector Ltd
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package im.vector.riotx.features.createdirect

import com.airbnb.epoxy.EpoxyController
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import im.vector.matrix.android.api.MatrixPatterns
import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.api.session.user.model.User
import im.vector.matrix.android.api.util.toMatrixItem
import im.vector.riotx.R
import im.vector.riotx.core.epoxy.errorWithRetryItem
import im.vector.riotx.core.epoxy.loadingItem
import im.vector.riotx.core.epoxy.noResultItem
import im.vector.riotx.core.error.ErrorFormatter
import im.vector.riotx.core.resources.StringProvider
import im.vector.riotx.features.home.AvatarRenderer
import javax.inject.Inject

class DirectoryUsersController @Inject constructor(private val session: Session,
                                                   private val avatarRenderer: AvatarRenderer,
                                                   private val stringProvider: StringProvider,
                                                   private val errorFormatter: ErrorFormatter) : EpoxyController() {

    private var state: CreateDirectRoomViewState? = null

    var callback: Callback? = null

    init {
        requestModelBuild()
    }

    fun setData(state: CreateDirectRoomViewState) {
        this.state = state
        requestModelBuild()
    }

    override fun buildModels() {
        val currentState = state ?: return
        val hasSearch = currentState.directorySearchTerm.isNotBlank()
        when (val asyncUsers = currentState.directoryUsers) {
            is Uninitialized -> renderEmptyState(false)
            is Loading       -> renderLoading()
            is Success       -> renderSuccess(getAsyncUsers(currentState), currentState.selectedUsers.map { it.userId }, hasSearch)
            is Fail          -> renderFailure(asyncUsers.error)
        }
    }

    private fun getAsyncUsers(currentState: CreateDirectRoomViewState): List<User> {
        return currentState
                .directoryUsers()
                ?.toMutableList()
                ?.apply {
                    currentState.directorySearchTerm
                            .takeIf { MatrixPatterns.isUserId(it) }
                            ?.let { add(User(it)) }
                } ?: emptyList()
    }

    private fun renderLoading() {
        loadingItem {
            id("loading")
        }
    }

    private fun renderFailure(failure: Throwable) {
        errorWithRetryItem {
            id("error")
            text(errorFormatter.toHumanReadable(failure))
            listener { callback?.retryDirectoryUsersRequest() }
        }
    }

    private fun renderSuccess(users: List<User>,
                              selectedUsers: List<String>,
                              hasSearch: Boolean) {
        if (users.isEmpty()) {
            renderEmptyState(hasSearch)
        } else {
            renderUsers(users, selectedUsers)
        }
    }

    private fun renderUsers(users: List<User>, selectedUsers: List<String>) {
        for (user in users) {
            if (user.userId == session.myUserId) {
                continue
            }
            val isSelected = selectedUsers.contains(user.userId)
            createDirectRoomUserItem {
                id(user.userId)
                selected(isSelected)
                matrixItem(user.toMatrixItem())
                avatarRenderer(avatarRenderer)
                clickListener { _ ->
                    callback?.onItemClick(user)
                }
            }
        }
    }

    private fun renderEmptyState(hasSearch: Boolean) {
        val noResultRes = if (hasSearch) {
            R.string.no_result_placeholder
        } else {
            R.string.direct_room_start_search
        }
        noResultItem {
            id("noResult")
            text(stringProvider.getString(noResultRes))
        }
    }

    interface Callback {
        fun onItemClick(user: User)
        fun retryDirectoryUsersRequest()
    }
}
