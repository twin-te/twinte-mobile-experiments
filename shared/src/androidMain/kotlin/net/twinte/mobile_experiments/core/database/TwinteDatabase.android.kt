package net.twinte.mobile_experiments.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room3.Room

@Composable
actual fun rememberTwinteDatabase(): TwinteDatabase {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        buildTwinteDatabase(
            Room.databaseBuilder<TwinteDatabase>(
                context = context,
                name = context.noBackupFilesDir.resolve(TWINTE_DATABASE_NAME).absolutePath,
                factory = TwinteDatabaseConstructor::initialize,
            ),
        )
    }
}
