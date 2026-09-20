package com.yourname.touchbase.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourname.touchbase.ui.callqueue.CallQueuePermissionGate
import com.yourname.touchbase.ui.callqueue.CallQueueScreen
import com.yourname.touchbase.ui.callqueue.ContactHistoryScreen
import com.yourname.touchbase.ui.callqueue.QueueBuilderScreen
import com.yourname.touchbase.dedupe.MergeDuplicatesScreen
import com.yourname.touchbase.ui.contacts.ContactListScreen
import com.yourname.touchbase.ui.contacts.ContactsPermissionGate
import com.yourname.touchbase.ui.contacts.CreateListScreen
import com.yourname.touchbase.ui.contacts.ManageListsScreen
import com.yourname.touchbase.ui.events.EventScreen
import com.yourname.touchbase.ui.sync.SyncSettingsScreen
import com.yourname.touchbase.ui.templates.TemplateScreen

object Routes {
    const val CONTACTS = "contacts"
    const val TEMPLATES = "templates"
    const val EVENTS = "events/{contactId}/{contactName}"
    const val QUEUE_BUILDER = "queue_builder"
    const val CALL_QUEUE = "call_queue/{sessionId}"
    const val SYNC_SETTINGS = "sync_settings"
    const val MERGE_DUPLICATES = "merge_duplicates"
    const val CREATE_LIST = "create_list?tagId={tagId}"
    const val MANAGE_LISTS = "manage_lists"
    const val CONTACT_HISTORY = "history/{contactId}/{contactName}"

    fun events(contactId: Long, contactName: String) = "events/$contactId/$contactName"
    fun callQueue(sessionId: Long) = "call_queue/$sessionId"
    fun createList(tagId: Long?) = "create_list?tagId=${tagId ?: -1L}"
    fun contactHistory(contactId: Long, contactName: String) = "history/$contactId/$contactName"
}

@Composable
fun TouchBaseNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CONTACTS) {

        composable(Routes.CONTACTS) {
            ContactsPermissionGate {
                ContactListScreen(
                    onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                    onOpenEvents = { contactId, contactName ->
                        navController.navigate(Routes.events(contactId, contactName))
                    },
                    onOpenQueueBuilder = { navController.navigate(Routes.QUEUE_BUILDER) },
                    onOpenSyncSettings = { navController.navigate(Routes.SYNC_SETTINGS) },
                    onOpenMergeDuplicates = { navController.navigate(Routes.MERGE_DUPLICATES) },
                    onOpenCreateList = { tagId -> navController.navigate(Routes.createList(tagId)) },
                    onOpenManageLists = { navController.navigate(Routes.MANAGE_LISTS) },
                    onOpenHistory = { contactId, contactName ->
                        navController.navigate(Routes.contactHistory(contactId, contactName))
                    }
                )
            }
        }

        composable(
            Routes.CONTACT_HISTORY,
            arguments = listOf(
                navArgument("contactId") { type = NavType.LongType },
                navArgument("contactName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactName = backStackEntry.arguments?.getString("contactName") ?: ""
            ContactHistoryScreen(contactName = contactName, onBack = { navController.popBackStack() })
        }

        composable(Routes.MERGE_DUPLICATES) {
            ContactsPermissionGate {
                MergeDuplicatesScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(
            Routes.CREATE_LIST,
            arguments = listOf(navArgument("tagId") { type = NavType.LongType; defaultValue = -1L })
        ) {
            ContactsPermissionGate {
                CreateListScreen(onDone = { navController.popBackStack() })
            }
        }

        composable(Routes.MANAGE_LISTS) {
            ContactsPermissionGate {
                ManageListsScreen(
                    onManageMembers = { tagId -> navController.navigate(Routes.createList(tagId)) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.TEMPLATES) {
            TemplateScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Routes.EVENTS,
            arguments = listOf(
                navArgument("contactId") { type = NavType.LongType },
                navArgument("contactName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: 0L
            val contactName = backStackEntry.arguments?.getString("contactName") ?: ""
            EventScreen(contactId = contactId, contactName = contactName, onBack = { navController.popBackStack() })
        }

        composable(Routes.QUEUE_BUILDER) {
            ContactsPermissionGate {
                QueueBuilderScreen(
                    onSessionReady = { sessionId ->
                        navController.navigate(Routes.callQueue(sessionId)) {
                            popUpTo(Routes.CONTACTS)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            Routes.CALL_QUEUE,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            CallQueuePermissionGate {
                CallQueueScreen(onFinished = { navController.popBackStack(Routes.CONTACTS, false) })
            }
        }

        composable(Routes.SYNC_SETTINGS) {
            SyncSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
