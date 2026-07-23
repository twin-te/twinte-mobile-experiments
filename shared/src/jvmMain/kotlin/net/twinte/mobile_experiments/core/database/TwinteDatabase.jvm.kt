package net.twinte.mobile_experiments.core.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room3.Room
import java.io.File

@Composable
actual fun rememberTwinteDatabase(): TwinteDatabase =
    remember {
        val databasePath = File(
            System.getProperty("java.io.tmpdir"),
            TWINTE_DATABASE_NAME,
        ).absolutePath
        buildTwinteDatabase(
            Room.databaseBuilder<TwinteDatabase>(
                name = databasePath,
                factory = TwinteDatabaseConstructor::initialize,
            ),
        )
    }
