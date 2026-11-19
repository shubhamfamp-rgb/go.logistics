package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.AppearanceMode
import com.example.data.BusinessProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "go_logistics_prefs")

class PreferencesStore(private val context: Context) {

    private object Keys {
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val COMPANY_NAME = stringPreferencesKey("company_name")
        val GSTIN = stringPreferencesKey("gstin")
        val OFFICE_ADDRESS = stringPreferencesKey("office_address")
        val CONTACT_PHONE = stringPreferencesKey("contact_phone")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val appearanceMode: Flow<AppearanceMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.APPEARANCE_MODE] ?: AppearanceMode.SYSTEM.name
        enumValues<AppearanceMode>().firstOrNull { it.name == raw } ?: AppearanceMode.SYSTEM
    }

    val businessProfile: Flow<BusinessProfile> = context.dataStore.data.map { prefs ->
        BusinessProfile(
            companyName = prefs[Keys.COMPANY_NAME] ?: "Shree Ganesh Roadlines",
            gstin = prefs[Keys.GSTIN] ?: "27AABCU9603R1ZM",
            officeAddress = prefs[Keys.OFFICE_ADDRESS] ?: "Transport Nagar, Nigdi, Pune 411044",
            contactPhone = prefs[Keys.CONTACT_PHONE] ?: "+91 98220 45120",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APPEARANCE_MODE] = mode.name
        }
    }

    suspend fun updateBusinessProfile(profile: BusinessProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMPANY_NAME] = profile.companyName
            prefs[Keys.GSTIN] = profile.gstin
            prefs[Keys.OFFICE_ADDRESS] = profile.officeAddress
            prefs[Keys.CONTACT_PHONE] = profile.contactPhone
            prefs[Keys.NOTIFICATIONS_ENABLED] = profile.notificationsEnabled
        }
    }
}
