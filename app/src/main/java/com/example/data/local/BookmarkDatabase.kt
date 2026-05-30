package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entity ---

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val id: String, // format: "surah_ayah"
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String,
    val arabicText: String,
    val urduTranslation: String,
    val personalNote: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Room DAO ---

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    fun getBookmarkById(id: String): Flow<Bookmark?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)
}

// --- Room Database ---

@Database(entities = [Bookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quran_tafseer_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository Pattern ---

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    fun getBookmark(surahNumber: Int, ayahNumber: Int): Flow<Bookmark?> {
        val id = "${surahNumber}_${ayahNumber}"
        return bookmarkDao.getBookmarkById(id)
    }

    suspend fun toggleBookmark(
        surahNumber: Int,
        ayahNumber: Int,
        surahName: String,
        arabicText: String,
        urduTranslation: String,
        isCurrentlyBookmarked: Boolean
    ) {
        val id = "${surahNumber}_${ayahNumber}"
        if (isCurrentlyBookmarked) {
            bookmarkDao.deleteBookmarkById(id)
        } else {
            val bookmark = Bookmark(
                id = id,
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                surahName = surahName,
                arabicText = arabicText,
                urduTranslation = urduTranslation,
                personalNote = ""
            )
            bookmarkDao.insertBookmark(bookmark)
        }
    }

    suspend fun updateNote(bookmark: Bookmark, note: String) {
        bookmarkDao.updateBookmark(bookmark.copy(personalNote = note))
    }

    suspend fun removeBookmark(id: String) {
        bookmarkDao.deleteBookmarkById(id)
    }
}
