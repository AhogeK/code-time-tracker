package com.ahogek.codetimetracker.action

import com.ahogek.codetimetracker.service.DataExportImportService
import com.ahogek.codetimetracker.ui.ExportDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Action to trigger the data export process.
 * Displays a configuration dialog to select date range, then a file saver dialog.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 14:48:51
 */
class ExportDataAction : AnAction("Export Data", "Export coding sessions to JSON", AllIcons.ToolbarDecorator.Export) {

    /**
     * Invoked when the action is performed.
     *
     * @param e Event carrying information about the action context.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 1. Show configuration dialog
        val dialog = ExportDialog(project)
        if (!dialog.showAndGet()) {
            return // User cancelled
        }

        // 2. Determine time range based on user input
        var startTime: LocalDateTime? = null
        var endTime: LocalDateTime? = null
        var fileNameSuffix = "all"

        if (!dialog.isExportAll()) {
            try {
                val startDate = LocalDate.parse(dialog.getStartDate())
                val endDate = LocalDate.parse(dialog.getEndDate())

                // Normalize to start of day and end of day
                startTime = startDate.atStartOfDay()
                endTime = endDate.atTime(LocalTime.MAX)
                fileNameSuffix = "${dialog.getStartDate()}-to-${dialog.getEndDate()}"

                if (startTime.isAfter(endTime)) {
                    Messages.showErrorDialog(project, "Start date must be before end date", "Invalid Range")
                    return
                }
            } catch (_: Exception) {
                // Dialog validation catches format errors, this catches logic errors
                return
            }
        }

        // 3. Show file save dialog
        val descriptor = FileSaverDescriptor("Export Data", "Choose location to save JSON file", "json")
        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val defaultFileName = "code-time-export-$fileNameSuffix.json"

        val fileWrapper = saveDialog.save(defaultFileName) ?: return

        // 4. Execute export via service
        val count = DataExportImportService.exportToFile(fileWrapper.file, startTime, endTime)

        if (count >= 0) {
            Messages.showInfoMessage(project, "Successfully exported $count sessions.", "Export Complete")
        } else {
            Messages.showErrorDialog(project, "Failed to export data. Check logs.", "Export Error")
        }
    }
}