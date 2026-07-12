package com.example.snapget.core.data

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.snapget.core.constants.FirestoreConfig
import com.example.snapget.core.model.Message
import com.example.snapget.core.model.Notification
import com.example.snapget.core.model.Post
import com.example.snapget.core.model.PostType
import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.User
import com.example.snapget.core.model.auth.AuthUser
import com.example.snapget.core.util.createdAtMillis
import com.example.snapget.core.util.isoDateTime
import com.example.snapget.core.util.mapToResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Repository responsible for reading/writing app data in Cloud Firestore.
 *
 * All queries intentionally use a SINGLE server-side filter (equality or
 * array-contains) and then sort/filter the rest in memory. This avoids having to
 * create Firestore composite indexes by hand — handy while prototyping.
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val tag = "FirestoreRepository"

    // Cache for user data to prevent duplicate reads
    private val userCache = mutableMapOf<String, AuthUser>()
    private var currentUserCache: AuthUser? = null

    suspend fun getCurrentUser(): AuthUser? {
        currentUserCache?.let { return it }

        return try {
            val fbUser = auth.currentUser ?: return null
            val authUser = AuthUser.fromFirebaseUser(fbUser)
            currentUserCache = authUser
            userCache[authUser.id] = authUser
            authUser
        } catch (e: Exception) {
            Log.e(tag, "Error fetching current user: ${e.message}")
            null
        }
    }

    suspend fun getUserByIdCustom(userId: String): AuthUser? {
        userCache[userId]?.let { return it }

        val currentUser = getCurrentUser()
        if (currentUser?.id == userId) return currentUser

        return getUserById(userId)
    }

    /**
     * Read a user profile from the `users` collection.
     */
    suspend fun getUserById(userId: String): AuthUser? {
        userCache[userId]?.let { return it }

        return try {
            val doc = firestore.collection(FirestoreConfig.USERS)
                .document(userId)
                .get()
                .await()

            if (!doc.exists()) {
                Log.d(tag, "No user document for ID: $userId")
                return null
            }

            val authUser = AuthUser(
                id = doc.id,
                email = doc.getString("email") ?: "",
                name = doc.getString("name") ?: doc.getString("username") ?: "",
                avatar = doc.getString("avatarUrl") ?: doc.getString("avatar") ?: "",
            )
            userCache[userId] = authUser
            authUser
        } catch (e: Exception) {
            Log.e(tag, "Error fetching user by ID: ${e.message}", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAllMessagesOfUser(userId: String): List<Message> {
        val snap = firestore.collection(FirestoreConfig.MESSAGES)
            .whereEqualTo("recipientId", userId)
            .limit(50)
            .get()
            .await()

        Log.d(tag, "Fetched ${snap.size()} message documents")
        return mapToResponse(snap, Message::fromDocument)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAllMessagesOfUserAndFriends(userId: String): List<Message> {
        val snap = firestore.collection(FirestoreConfig.MESSAGES)
            .whereEqualTo("senderId", userId)
            .limit(50)
            .get()
            .await()

        Log.d(tag, "Fetched ${snap.size()} message documents")
        return mapToResponse(snap, Message::fromDocument)
    }

    /**
     * Get all posts from a user and their friends, newest first.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAllPostsOfUserAndFriends(user: AuthUser): List<Post> {
        return try {
            val friendIds = friendIdsOf(user.id)
            Log.d(tag, "Fetched ${friendIds.size} friend IDs for user ${user.id}")

            // Own posts
            val ownDocs = firestore.collection(FirestoreConfig.POSTS)
                .whereEqualTo("userId", user.id)
                .get()
                .await()
                .documents
                .filter { it.getBoolean("isArchived") != true }

            // Friends' posts (chunked because `whereIn` is capped)
            val friendDocs = fetchFriendsPosts(friendIds)

            val combined = (ownDocs + friendDocs)
                .sortedByDescending { it.createdAtMillis() }
                .take(50)

            val usersMap = getUsersByIds(combined.mapNotNull { it.getString("userId") }.distinct())

            combined.mapNotNull { doc ->
                val postUser = usersMap[doc.getString("userId")] ?: return@mapNotNull null
                postFromDoc(doc, postUser)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching posts: ${e.message}", e)
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getPostsForUser(userId: String, viewerId: String? = null): List<Post> {
        return try {
            var docs = firestore.collection(FirestoreConfig.POSTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .filter { it.getBoolean("isArchived") != true }

            // Visibility filtering when the viewer is not the owner
            if (viewerId != null && viewerId != userId) {
                val areFriends = checkIfUsersAreFriends(viewerId, userId)
                docs = docs.filter { doc ->
                    when (doc.getString("visibility") ?: "PUBLIC") {
                        "PUBLIC" -> true
                        "FRIEND" -> areFriends
                        else -> false
                    }
                }
            }

            docs = docs.sortedByDescending { it.createdAtMillis() }.take(50)

            val usersMap = getUsersByIds(docs.mapNotNull { it.getString("userId") }.distinct())

            docs.mapNotNull { doc ->
                val postUser = usersMap[doc.getString("userId")] ?: return@mapNotNull null
                postFromDoc(doc, postUser)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching user posts: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Check whether two users have an ACCEPTED friendship.
     */
    suspend fun checkIfUsersAreFriends(userId1: String, userId2: String): Boolean = try {
        val snap = firestore.collection(FirestoreConfig.FRIENDSHIPS)
            .whereArrayContains("combinedUserIds", userId1)
            .get()
            .await()

        snap.documents.any { doc ->
            (doc.getString("status") ?: "") == "ACCEPTED" &&
                combinedIdsOf(doc).contains(userId2)
        }
    } catch (e: Exception) {
        Log.e(tag, "Error checking friendship: ${e.message}")
        false
    }

    /**
     * Get public posts that contain any of the given tags.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getPostsByTags(tags: List<String>, viewerId: String?, limit: Int = 50): List<Post> {
        if (tags.isEmpty()) return emptyList()

        return try {
            val docs = firestore.collection(FirestoreConfig.POSTS)
                .whereArrayContainsAny("tags", tags)
                .get()
                .await()
                .documents
                .filter {
                    it.getBoolean("isArchived") != true &&
                        (it.getString("visibility") ?: "PUBLIC") == "PUBLIC"
                }
                .sortedByDescending { it.createdAtMillis() }
                .take(limit)

            val usersMap = getUsersByIds(docs.mapNotNull { it.getString("userId") }.distinct())

            docs.mapNotNull { doc ->
                val postUser = usersMap[doc.getString("userId")] ?: return@mapNotNull null
                postFromDoc(doc, postUser)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching posts by tags: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get the accepted friends of a user.
     */
    suspend fun getFriendsOfUser(user: AuthUser): List<User> {
        return try {
            val friendIds = friendIdsOf(user.id)
            if (friendIds.isEmpty()) return emptyList()

            val friendsMap = getUsersByIds(friendIds)
            friendsMap.values.map { User.mapToUser(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching friends: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getAllNotificationOfUser(user: AuthUser): List<Notification> {
        val snap = firestore.collection(FirestoreConfig.NOTIFICATIONS)
            .whereEqualTo("userId", user.id)
            .limit(50)
            .get()
            .await()

        Log.d(tag, "Fetched ${snap.size()} notification documents")
        return mapToResponse(snap, Notification::fromDocument)
    }

    suspend fun setReadNotification(notificationId: String): Notification {
        val ref = firestore.collection(FirestoreConfig.NOTIFICATIONS).document(notificationId)
        ref.update("isRead", true).await()
        return Notification.fromDocument(ref.get().await())
    }

    suspend fun getAllSetting(): List<Setting> {
        val snap = firestore.collection(FirestoreConfig.SETTINGS)
            .limit(50)
            .get()
            .await()

        Log.d(tag, "Fetched ${snap.size()} setting documents")
        return mapToResponse(snap, Setting::fromDocument)
    }

    suspend fun updateSetting(setting: Setting): Setting {
        val ref = firestore.collection(FirestoreConfig.SETTINGS).document(setting.id)
        val data = mapOf(
            "title" to setting.title,
            "description" to setting.description,
            "icon" to setting.icon,
            "type" to setting.type.name,
            "isToggleable" to setting.isToggleable,
            "isToggled" to setting.isToggled,
        )
        ref.update(data).await()
        return Setting.fromDocument(ref.get().await())
    }

    /**
     * Batch fetch multiple users, using the cache where possible.
     */
    suspend fun getUsersByIds(userIds: List<String>): Map<String, AuthUser> {
        val result = mutableMapOf<String, AuthUser>()
        val uncached = mutableListOf<String>()

        userIds.forEach { userId ->
            userCache[userId]?.let { result[userId] = it } ?: uncached.add(userId)
        }

        uncached.forEach { userId ->
            getUserById(userId)?.let { result[userId] = it }
        }

        Log.d(tag, "Batch fetched ${userIds.size} users: ${result.size} resolved")
        return result
    }

    fun clearUserCache() {
        userCache.clear()
        currentUserCache = null
        Log.d(tag, "User cache cleared")
    }

    fun clearUserFromCache(userId: String) {
        userCache.remove(userId)
        if (currentUserCache?.id == userId) currentUserCache = null
        Log.d(tag, "Cleared cache for user: $userId")
    }

    // ---- Helpers -----------------------------------------------------------

    /** Returns the other-user ids of all ACCEPTED friendships of [userId]. */
    private suspend fun friendIdsOf(userId: String): List<String> {
        val snap = firestore.collection(FirestoreConfig.FRIENDSHIPS)
            .whereArrayContains("combinedUserIds", userId)
            .limit(100)
            .get()
            .await()

        return snap.documents
            .filter { (it.getString("status") ?: "") == "ACCEPTED" }
            .mapNotNull { doc -> combinedIdsOf(doc).firstOrNull { it != userId } }
    }

    /** Fetch friends' posts, chunking friend ids because `whereIn` is capped at 10. */
    private suspend fun fetchFriendsPosts(friendIds: List<String>): List<DocumentSnapshot> {
        if (friendIds.isEmpty()) return emptyList()

        val docs = mutableListOf<DocumentSnapshot>()
        friendIds.chunked(10).forEach { chunk ->
            val snap = firestore.collection(FirestoreConfig.POSTS)
                .whereIn("userId", chunk)
                .get()
                .await()

            docs += snap.documents.filter { doc ->
                doc.getBoolean("isArchived") != true &&
                    (doc.getString("visibility") ?: "PUBLIC").let { it == "PUBLIC" || it == "FRIEND" }
            }
        }
        return docs
    }

    private fun combinedIdsOf(doc: DocumentSnapshot): List<String> = (doc.get("combinedUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun postFromDoc(doc: DocumentSnapshot, postUser: AuthUser): Post? = try {
        @Suppress("UNCHECKED_CAST")
        Post(
            id = doc.id,
            user = User.mapToUser(postUser),
            postType = runCatching { PostType.valueOf(doc.getString("postType") ?: "IMAGE") }
                .getOrDefault(PostType.IMAGE),
            caption = doc.getString("caption"),
            thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
            isArchived = doc.getBoolean("isArchived") ?: false,
            createdAt = doc.isoDateTime("createdAt"),
            visibility = doc.getString("visibility") ?: "PUBLIC",
            friendsOnly = doc.getBoolean("friendsOnly") ?: false,
            tags = (doc.get("tags") as? List<String>) ?: emptyList(),
            updatedAt = doc.isoDateTime("updatedAt").ifEmpty { null },
        )
    } catch (e: Exception) {
        Log.e(tag, "Error mapping post document ${doc.id}: ${e.message}")
        null
    }
}
