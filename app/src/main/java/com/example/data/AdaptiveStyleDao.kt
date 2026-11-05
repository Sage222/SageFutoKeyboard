package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdaptiveStyleDao {
    @Query("SELECT * FROM key_spatial_profiles ORDER BY keyChar ASC")
    fun observeKeySpatialProfiles(): Flow<List<KeySpatialProfileEntity>>

    @Query("SELECT * FROM key_spatial_profiles")
    suspend fun getKeySpatialProfilesSnapshot(): List<KeySpatialProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKeySpatialProfile(profile: KeySpatialProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKeySpatialProfiles(profiles: List<KeySpatialProfileEntity>)

    @Query("SELECT * FROM user_word_frequencies ORDER BY (personalUseCount * 25 + correctionSelectCount * 40 + baseFrequency) DESC")
    fun observeUserWords(): Flow<List<UserWordFrequencyEntity>>

    @Query("SELECT * FROM user_word_frequencies")
    suspend fun getUserWordsSnapshot(): List<UserWordFrequencyEntity>

    @Query("SELECT * FROM user_word_frequencies WHERE word = :word LIMIT 1")
    suspend fun getUserWord(word: String): UserWordFrequencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserWord(wordEntity: UserWordFrequencyEntity)

    @Query("DELETE FROM user_word_frequencies WHERE word = :word")
    suspend fun deleteUserWord(word: String)

    @Query("SELECT * FROM user_bigrams ORDER BY transitionCount DESC, lastUpdated DESC")
    fun observeBigrams(): Flow<List<UserBigramEntity>>

    @Query("SELECT * FROM user_bigrams")
    suspend fun getBigramsSnapshot(): List<UserBigramEntity>

    @Query("SELECT * FROM user_bigrams WHERE prevWord = :prevWord AND nextWord = :nextWord LIMIT 1")
    suspend fun getBigram(prevWord: String, nextWord: String): UserBigramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBigram(bigram: UserBigramEntity)

    @Query("SELECT * FROM gesture_prototypes ORDER BY sampleCount DESC, lastUpdated DESC")
    fun observeGesturePrototypes(): Flow<List<GesturePrototypeEntity>>

    @Query("SELECT * FROM gesture_prototypes")
    suspend fun getGesturePrototypesSnapshot(): List<GesturePrototypeEntity>

    @Query("SELECT * FROM gesture_prototypes WHERE word = :word LIMIT 1")
    suspend fun getGesturePrototype(word: String): GesturePrototypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGesturePrototype(prototype: GesturePrototypeEntity)

    @Query("SELECT * FROM gesture_session_logs ORDER BY timestamp DESC LIMIT 60")
    fun observeRecentLogs(): Flow<List<GestureSessionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: GestureSessionLogEntity)

    @Query("DELETE FROM key_spatial_profiles")
    suspend fun clearSpatialProfiles()

    @Query("DELETE FROM user_word_frequencies")
    suspend fun clearUserWords()

    @Query("DELETE FROM user_bigrams")
    suspend fun clearBigrams()

    @Query("DELETE FROM gesture_prototypes")
    suspend fun clearGesturePrototypes()

    @Query("DELETE FROM gesture_session_logs")
    suspend fun clearSessionLogs()
}
