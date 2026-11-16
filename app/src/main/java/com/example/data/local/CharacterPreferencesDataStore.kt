package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AudioRoutePreference
import com.example.data.model.CharacterAppearance
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.VoicePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.characterCustomizationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "character_customization_prefs"
)

data class PersistedCharacterCustomization(
    val appearance: CharacterAppearance,
    val clothing: ClothingAndAccessories,
    val voicePreferences: VoicePreferences = VoicePreferences(),
    val activeCategoryId: String = "silhouette"
)

class CharacterPreferencesDataStore(
    private val context: Context
) {
    private val dataStore: DataStore<Preferences>
        get() = context.applicationContext.characterCustomizationDataStore

    companion object {
        private val HAS_SAVED_CUSTOMIZATION = booleanPreferencesKey("has_saved_customization")
        private val SKIN_TONE_ID = stringPreferencesKey("skin_tone_id")
        private val HAIR_STYLE_ID = stringPreferencesKey("hair_style_id")
        private val HAIR_COLOR_ID = stringPreferencesKey("hair_color_id")
        private val EYE_STYLE_ID = stringPreferencesKey("eye_style_id")
        private val EYE_COLOR_ID = stringPreferencesKey("eye_color_id")
        private val EXPRESSION_DEFAULT = stringPreferencesKey("expression_default")
        private val BACKDROP_THEME_ID = stringPreferencesKey("backdrop_theme_id")

        private val OUTFIT_ID = stringPreferencesKey("outfit_id")
        private val PRIMARY_COLOR_HEX = stringPreferencesKey("primary_color_hex")
        private val ACCENT_COLOR_HEX = stringPreferencesKey("accent_color_hex")
        private val ACCESSORY_ID = stringPreferencesKey("accessory_id")
        private val AURA_EFFECT_ID = stringPreferencesKey("aura_effect_id")

        private val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        private val VOICE_SPEECH_RATE = floatPreferencesKey("voice_speech_rate")
        private val VOICE_HANDS_FREE = booleanPreferencesKey("voice_hands_free")
        private val VOICE_AUDIO_ROUTE = stringPreferencesKey("voice_audio_route")

        private val ACTIVE_CATEGORY_ID = stringPreferencesKey("active_category_id")
    }

    val customizationFlow: Flow<PersistedCharacterCustomization?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs.toPersistedCharacterCustomization()
        }

    suspend fun readCustomization(): PersistedCharacterCustomization? {
        return runCatching {
            customizationFlow.first()
        }.getOrNull()
    }

    suspend fun saveAppearance(appearance: CharacterAppearance) {
        dataStore.edit { prefs ->
            prefs[HAS_SAVED_CUSTOMIZATION] = true
            prefs[SKIN_TONE_ID] = appearance.skinToneId
            prefs[HAIR_STYLE_ID] = appearance.hairStyleId
            prefs[HAIR_COLOR_ID] = appearance.hairColorId
            prefs[EYE_STYLE_ID] = appearance.eyeStyleId
            prefs[EYE_COLOR_ID] = appearance.eyeColorId
            prefs[EXPRESSION_DEFAULT] = appearance.expressionDefault
            prefs[BACKDROP_THEME_ID] = appearance.backdropThemeId
        }
    }

    suspend fun saveClothing(clothing: ClothingAndAccessories) {
        dataStore.edit { prefs ->
            prefs[HAS_SAVED_CUSTOMIZATION] = true
            prefs[OUTFIT_ID] = clothing.outfitId
            prefs[PRIMARY_COLOR_HEX] = clothing.primaryColorHex
            prefs[ACCENT_COLOR_HEX] = clothing.accentColorHex
            prefs[ACCESSORY_ID] = clothing.accessoryId
            prefs[AURA_EFFECT_ID] = clothing.auraEffectId
        }
    }

    suspend fun saveVoicePreferences(voicePreferences: VoicePreferences) {
        dataStore.edit { prefs ->
            prefs[HAS_SAVED_CUSTOMIZATION] = true
            prefs[VOICE_PITCH] = voicePreferences.pitch
            prefs[VOICE_SPEECH_RATE] = voicePreferences.speechRate
            prefs[VOICE_HANDS_FREE] = voicePreferences.handsFreeAutoListen
            prefs[VOICE_AUDIO_ROUTE] = voicePreferences.preferredAudioRoute.name
        }
    }

    suspend fun saveAppearanceAndClothing(
        appearance: CharacterAppearance,
        clothing: ClothingAndAccessories,
        voicePreferences: VoicePreferences? = null
    ) {
        dataStore.edit { prefs ->
            prefs[HAS_SAVED_CUSTOMIZATION] = true
            prefs[SKIN_TONE_ID] = appearance.skinToneId
            prefs[HAIR_STYLE_ID] = appearance.hairStyleId
            prefs[HAIR_COLOR_ID] = appearance.hairColorId
            prefs[EYE_STYLE_ID] = appearance.eyeStyleId
            prefs[EYE_COLOR_ID] = appearance.eyeColorId
            prefs[EXPRESSION_DEFAULT] = appearance.expressionDefault
            prefs[BACKDROP_THEME_ID] = appearance.backdropThemeId

            prefs[OUTFIT_ID] = clothing.outfitId
            prefs[PRIMARY_COLOR_HEX] = clothing.primaryColorHex
            prefs[ACCENT_COLOR_HEX] = clothing.accentColorHex
            prefs[ACCESSORY_ID] = clothing.accessoryId
            prefs[AURA_EFFECT_ID] = clothing.auraEffectId

            if (voicePreferences != null) {
                prefs[VOICE_PITCH] = voicePreferences.pitch
                prefs[VOICE_SPEECH_RATE] = voicePreferences.speechRate
                prefs[VOICE_HANDS_FREE] = voicePreferences.handsFreeAutoListen
                prefs[VOICE_AUDIO_ROUTE] = voicePreferences.preferredAudioRoute.name
            }
        }
    }

    suspend fun saveActiveCategory(categoryId: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_CATEGORY_ID] = categoryId
        }
    }

    suspend fun clearCustomization() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun Preferences.toPersistedCharacterCustomization(): PersistedCharacterCustomization? {
        val hasSaved = this[HAS_SAVED_CUSTOMIZATION] ?: false
        if (!hasSaved) return null

        val defaultsApp = CharacterAppearance()
        val defaultsCloth = ClothingAndAccessories()
        val defaultsVoice = VoicePreferences()

        val appearance = CharacterAppearance(
            skinToneId = this[SKIN_TONE_ID] ?: defaultsApp.skinToneId,
            hairStyleId = this[HAIR_STYLE_ID] ?: defaultsApp.hairStyleId,
            hairColorId = this[HAIR_COLOR_ID] ?: defaultsApp.hairColorId,
            eyeStyleId = this[EYE_STYLE_ID] ?: defaultsApp.eyeStyleId,
            eyeColorId = this[EYE_COLOR_ID] ?: defaultsApp.eyeColorId,
            expressionDefault = this[EXPRESSION_DEFAULT] ?: defaultsApp.expressionDefault,
            backdropThemeId = this[BACKDROP_THEME_ID] ?: defaultsApp.backdropThemeId
        )

        val clothing = ClothingAndAccessories(
            outfitId = this[OUTFIT_ID] ?: defaultsCloth.outfitId,
            primaryColorHex = this[PRIMARY_COLOR_HEX] ?: defaultsCloth.primaryColorHex,
            accentColorHex = this[ACCENT_COLOR_HEX] ?: defaultsCloth.accentColorHex,
            accessoryId = this[ACCESSORY_ID] ?: defaultsCloth.accessoryId,
            auraEffectId = this[AURA_EFFECT_ID] ?: defaultsCloth.auraEffectId
        )

        val routeName = this[VOICE_AUDIO_ROUTE]
        val route = AudioRoutePreference.entries.firstOrNull { it.name == routeName }
            ?: defaultsVoice.preferredAudioRoute

        val voicePreferences = VoicePreferences(
            pitch = this[VOICE_PITCH] ?: defaultsVoice.pitch,
            speechRate = this[VOICE_SPEECH_RATE] ?: defaultsVoice.speechRate,
            handsFreeAutoListen = this[VOICE_HANDS_FREE] ?: defaultsVoice.handsFreeAutoListen,
            preferredAudioRoute = route
        )

        val activeCategory = this[ACTIVE_CATEGORY_ID] ?: "silhouette"
        return PersistedCharacterCustomization(
            appearance = appearance,
            clothing = clothing,
            voicePreferences = voicePreferences,
            activeCategoryId = activeCategory
        )
    }
}
