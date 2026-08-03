package net.twinte.mobile_experiments.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURLIsExcludedFromBackupKey

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
        val databaseDirectory = requireNotNull(
            applicationSupportUrl.URLByAppendingPathComponent(DatabaseDirectoryName, isDirectory = true),
        )
        check(
            NSFileManager.defaultManager.createDirectoryAtURL(
                url = databaseDirectory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        )
        check(
            databaseDirectory.setResourceValue(
                value = true,
                forKey = NSURLIsExcludedFromBackupKey,
                error = null,
            ),
        )
        val databasePath = requireNotNull(
            requireNotNull(databaseDirectory.URLByAppendingPathComponent(TWINTE_DATABASE_NAME)).path,
        )
        buildTwinteDatabase(
            Room.databaseBuilder<TwinteDatabase>(
                name = databasePath,
                factory = TwinteDatabaseConstructor::initialize,
            ),
        )
    }

private const val DatabaseDirectoryName = "OfflineData"
