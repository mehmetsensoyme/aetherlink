package org.aetherlink.util

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

enum class AppThemeMode(val title: String) {
    SYSTEM("Sistem"),
    LIGHT("Açık"),
    DARK("Koyu")
}

object ThemePreferences {
    private const val PREFS_NAME = "aetherlink_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    private val _themeModeState = mutableStateOf(AppThemeMode.SYSTEM)
    val themeModeState: State<AppThemeMode> = _themeModeState

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        _themeModeState.value = try {
            AppThemeMode.valueOf(saved)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeModeState.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try {
            AppThemeMode.valueOf(saved)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }
}
