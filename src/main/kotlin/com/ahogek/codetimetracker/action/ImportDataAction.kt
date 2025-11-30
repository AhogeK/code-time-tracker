package com.ahogek.codetimetracker.action

import com.ahogek.codetimetracker.service.DataExportImportService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages

/**
 * Action to trigger the data import process.
 * Opens a file chooser for the user to select a JSON file and passes it to the service.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 14:47:22
 */
class ImportDataAction : AnAction("Import Data", "Import coding sessions from JSON", AllIcons.ToolbarDecorator.Import) {

    /**
     * Invoked when the action is performed.
     *
     * @param e Event carrying information about the action context.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Configure file chooser for JSON files
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        descriptor.title = "Select Export File to Import"

        val selectedFile = FileChooser.chooseFile(descriptor, project, null)

        if (selectedFile != null) {
            // Delegate the actual logic to the service
            val result = DataExportImportService.importFromFile(selectedFile.toNioPath().toFile())

            if (result.success) {
                // Build a detailed result message
                val message = buildString {
                    appendLine("Import completed successfully!")
                    appendLine("Total in file: ${result.totalInFile}")
                    appendLine("Imported: ${result.imported}")
                    appendLine("Skipped: ${result.skipped}")
                    if (result.failed > 0) appendLine("Failed: ${result.failed}")
                }
                Messages.showInfoMessage(project, message, "Import Successful")
            } else {
                Messages.showErrorDialog(project, result.errorMessage ?: "Unknown error", "Import Failed")
            }
        }
    }
}