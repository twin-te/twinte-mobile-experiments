package net.twinte.mobile_experiments.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@Composable
@OptIn(ExperimentalForeignApi::class)
actual fun rememberTwinteDatabase(): TwinteDatabase =
    remember {
        val applicationSupportUrl = requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        )
        val databasePath = requireNotNull(
            requireNotNull(
                applicationSupportUrl.URLByAppendingPathComponent(TWINTE_DATABASE_NAME),
            ).path,
        )
        buildTwinteDatabase(
            Room.databaseBuilder<TwinteDatabase>(
                name = databasePath,
                factory = TwinteDatabaseConstructor::initialize,
            ),
        )
    }
