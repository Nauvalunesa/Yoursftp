package com.yoursftp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yoursftp.app.ui.AppViewModelFactory
import com.yoursftp.app.ui.BrowserViewModel
import com.yoursftp.app.ui.ConnectionsViewModel
import com.yoursftp.app.ui.EditConnectionViewModel
import com.yoursftp.app.ui.EditorViewModel
import com.yoursftp.app.editor.LargeEditorViewModel
import com.yoursftp.app.editor.LargeEditorScreen
import com.yoursftp.app.db.DbViewModel
import com.yoursftp.app.db.DbViewerScreen
import com.yoursftp.app.ui.Routes
import com.yoursftp.app.ui.screens.BrowserScreen
import com.yoursftp.app.ui.screens.ConnectionsScreen
import com.yoursftp.app.ui.screens.EditConnectionScreen
import com.yoursftp.app.ui.screens.EditorScreen
import com.yoursftp.app.ui.theme.YoursFtpTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentTransitionScope
import com.yoursftp.app.ui.TerminalViewModel
import com.yoursftp.app.ui.screens.TerminalScreen
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val factory by lazy { AppViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YoursFtpTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav(factory)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNav(factory: AppViewModelFactory) {
    val nav = rememberNavController()

    // Browser & Editor berbagi sesi yang sama; scope ViewModel ke Activity
    // via factory tunggal agar SessionManager konsisten.
    NavHost(
        navController = nav,
        startDestination = Routes.CONNECTIONS,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)) }
    ) {

        composable(Routes.CONNECTIONS) {
            val vm: ConnectionsViewModel = viewModel(factory)
            ConnectionsScreen(
                vm = vm,
                onAdd = { nav.navigate(Routes.editConnection(null)) },
                onEdit = { nav.navigate(Routes.editConnection(it.id)) },
                onConnect = { nav.navigate(Routes.browser(it.id)) },
                onOpenTerminal = { nav.navigate(Routes.terminal(it.id)) }
            )
        }

        composable(
            route = "${Routes.EDIT_CONNECTION}?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            val vm: EditConnectionViewModel = viewModel(factory)
            val id = entry.arguments?.getLong("id") ?: -1L
            EditConnectionScreen(vm = vm, connectionId = id, onBack = { nav.popBackStack() })
        }

        composable(
            route = "${Routes.BROWSER}/{connectionId}",
            arguments = listOf(navArgument("connectionId") { type = NavType.LongType })
        ) { entry ->
            val vm: BrowserViewModel = viewModel(factory)
            val connId = entry.arguments?.getLong("connectionId") ?: 0L
            BrowserScreen(
                vm = vm,
                connectionId = connId,
                onBack = { nav.popBackStack() },
                onEditFile = { path, engine ->
                    val encoded = URLEncoder.encode(path, "UTF-8")
                    nav.navigate("${Routes.EDITOR}?path=$encoded&engine=$engine")
                },
                onOpenTerminal = { id ->
                    nav.navigate(Routes.terminal(id))
                },
                onOpenDb = { localPath, title ->
                    nav.navigate(Routes.dbViewer(localPath, title))
                }
            )
        }

        composable(
            route = "${Routes.EDITOR}?path={path}&engine={engine}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
                navArgument("engine") { type = NavType.StringType; defaultValue = "rich" }
            )
        ) { entry ->
            val raw = entry.arguments?.getString("path").orEmpty()
            val path = if (raw.isEmpty()) "" else URLDecoder.decode(raw, "UTF-8")
            val engine = entry.arguments?.getString("engine") ?: "rich"
            if (engine == "large") {
                val vm: LargeEditorViewModel = viewModel(factory)
                LargeEditorScreen(vm = vm, path = path, onBack = { nav.popBackStack() })
            } else {
                val vm: EditorViewModel = viewModel(factory)
                EditorScreen(vm = vm, path = path, onBack = { nav.popBackStack() })
            }
        }

        composable(
            route = "${Routes.TERMINAL}/{connectionId}",
            arguments = listOf(navArgument("connectionId") { type = NavType.LongType })
        ) { entry ->
            val vm: TerminalViewModel = viewModel(factory)
            val connId = entry.arguments?.getLong("connectionId") ?: 0L
            TerminalScreen(vm = vm, connectionId = connId, onBack = { nav.popBackStack() })
        }

        composable(
            route = "${Routes.DB_VIEWER}?path={path}&title={title}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "Database" }
            )
        ) { entry ->
            val vm: DbViewModel = viewModel(factory)
            val rawPath = entry.arguments?.getString("path").orEmpty()
            val localPath = if (rawPath.isEmpty()) "" else URLDecoder.decode(rawPath, "UTF-8")
            val rawTitle = entry.arguments?.getString("title").orEmpty()
            val title = if (rawTitle.isEmpty()) "Database" else URLDecoder.decode(rawTitle, "UTF-8")
            DbViewerScreen(vm = vm, localPath = localPath, title = title, onBack = { nav.popBackStack() })
        }
    }
}

/** Helper viewModel() yang menerima factory tunggal. */
@androidx.compose.runtime.Composable
private inline fun <reified T : androidx.lifecycle.ViewModel> viewModel(
    factory: AppViewModelFactory
): T {
    val owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!
    return ViewModelProvider(owner, factory)[T::class.java]
}
