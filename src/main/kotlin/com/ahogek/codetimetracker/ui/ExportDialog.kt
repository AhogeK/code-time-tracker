package com.ahogek.codetimetracker.ui

import com.github.lgooddatepicker.components.DatePicker
import com.github.lgooddatepicker.components.DatePickerSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JRadioButton

/**
 * A dialog wrapper allowing users to configure data export options.
 *
 * This dialog provides two modes:
 * 1. Export all historical data.
 * 2. Export data within a specific date range using a visual calendar picker.
 *
 * Implementation Note:
 * Uses LGoodDatePicker library for a rich, calendar-popup user experience.
 *
 * @author AhogeK ahogek@gmail.com
 * @since 2025-11-30 14:44:05
 */
class ExportDialog(project: Project?) : DialogWrapper(project) {

    private val allDataRadio = JRadioButton("Export all data", true)
    private val dateRangeRadio = JRadioButton("Export by date range")

    private val startDatePicker = createDatePicker(LocalDate.now().minusMonths(1))
    private val endDatePicker = createDatePicker(LocalDate.now())

    init {
        title = "Export Coding Sessions"

        val group = ButtonGroup()
        group.add(allDataRadio)
        group.add(dateRangeRadio)

        val toggleFields = {
            startDatePicker.isEnabled = dateRangeRadio.isSelected
            endDatePicker.isEnabled = dateRangeRadio.isSelected
        }

        allDataRadio.addActionListener { toggleFields() }
        dateRangeRadio.addActionListener { toggleFields() }

        toggleFields()
        init()
    }

    /**
     * Creates and configures a [DatePicker] with IDE-friendly settings.
     *
     * @param defaultDate The initial date to display.
     * @return A configured DatePicker instance.
     */
    private fun createDatePicker(defaultDate: LocalDate): DatePicker {
        val settings = DatePickerSettings()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        settings.formatForDatesCommonEra = formatter
        settings.formatForDatesBeforeCommonEra = formatter

        // Check if the IDE is in Dark Theme
        if (!JBColor.isBright()) {
            // --- Enhanced Color Palette (Monochrome Dark Style) ---
            val panelBg = UIUtil.getPanelBackground() // #3C3F41
            val brightText = JBColor(0xBBBBBB, 0xBBBBBB) // Bright gray text
            val dimText = JBColor(0x787878, 0x787878) // Dimmed gray for secondary
            val selectionBg = JBColor(0x4C5052, 0x4C5052) // Dark gray selection (no blue)
            val todayAccent = JBColor(0xBBBBBB, 0xBBBBBB) // Same as normal text, no special color
            val borderColor = JBColor(0x555555, 0x555555) // Subtle border
            val weekdayBg = JBColor(0x45494A, 0x45494A) // Dark gray for weekday header (no blue)

            // --- Backgrounds (Unified Dark Theme) ---
            settings.setColor(DatePickerSettings.DateArea.BackgroundOverallCalendarPanel, panelBg)
            settings.setColor(DatePickerSettings.DateArea.BackgroundMonthAndYearMenuLabels, panelBg)
            settings.setColor(DatePickerSettings.DateArea.BackgroundTodayLabel, panelBg)
            settings.setColor(DatePickerSettings.DateArea.BackgroundClearLabel, panelBg)
            settings.setColor(DatePickerSettings.DateArea.BackgroundMonthAndYearNavigationButtons, panelBg)
            settings.setColor(DatePickerSettings.DateArea.BackgroundTopLeftLabelAboveWeekNumbers, weekdayBg)
            settings.setColor(DatePickerSettings.DateArea.CalendarBackgroundNormalDates, panelBg)

            // Selected date background (subtle gray, no blue)
            settings.setColor(DatePickerSettings.DateArea.CalendarBackgroundSelectedDate, selectionBg)

            // Vetoed dates background
            settings.setColor(DatePickerSettings.DateArea.CalendarBackgroundVetoedDates, panelBg)

            // --- Weekday header background (with border matching parameter) ---
            settings.setColorBackgroundWeekdayLabels(weekdayBg, true)
            settings.setColorBackgroundWeekNumberLabels(weekdayBg, true)

            // --- Borders (Subtle structure) ---
            settings.setColor(DatePickerSettings.DateArea.CalendarBorderSelectedDate, borderColor)

            // --- Text Colors (High Contrast Monochrome) ---

            // Month/Year label -> Bright white
            settings.setColor(DatePickerSettings.DateArea.TextMonthAndYearMenuLabels, brightText)

            // Normal dates -> Bright readable white
            settings.setColor(DatePickerSettings.DateArea.CalendarTextNormalDates, brightText)

            // Today label -> Same as normal (no accent)
            settings.setColor(DatePickerSettings.DateArea.TextTodayLabel, todayAccent)

            // Clear label -> Bright white
            settings.setColor(DatePickerSettings.DateArea.TextClearLabel, brightText)

            // Weekdays header (Sun, Mon...) -> Dimmed but readable
            settings.setColor(DatePickerSettings.DateArea.CalendarTextWeekdays, dimText)

            // Week numbers -> Dimmed gray
            settings.setColor(DatePickerSettings.DateArea.CalendarTextWeekNumbers, dimText)

            // Hover state background
            settings.setColor(
                DatePickerSettings.DateArea.BackgroundCalendarPanelLabelsOnHover,
                JBColor(0x4A4D4F, 0x4A4D4F)
            )

            // Hover state text
            settings.setColor(DatePickerSettings.DateArea.TextCalendarPanelLabelsOnHover, brightText)

            // --- Text Field Colors (critical fix for input visibility) ---
            settings.setColor(DatePickerSettings.DateArea.DatePickerTextValidDate, brightText)
            settings.setColor(DatePickerSettings.DateArea.DatePickerTextVetoedDate, brightText)
            settings.setColor(DatePickerSettings.DateArea.TextFieldBackgroundValidDate, panelBg)
            settings.setColor(DatePickerSettings.DateArea.TextFieldBackgroundVetoedDate, panelBg)

            // --- Navigation Buttons ---
            settings.setColor(DatePickerSettings.DateArea.TextMonthAndYearNavigationButtons, brightText)
            settings.setColor(DatePickerSettings.DateArea.BackgroundMonthAndYearNavigationButtons, panelBg)
        }

        // Disable keyboard editing for consistency
        settings.allowKeyboardEditing = false

        val picker = DatePicker(settings)
        picker.date = defaultDate

        // --- Text Field Styling (IDE-consistent with forced colors) ---
        picker.componentDateTextField.apply {
            isEditable = false
            border = JBUI.Borders.empty(0, 5)
            background = UIUtil.getTextFieldBackground()
            // Force bright foreground color for dark theme visibility
            foreground = if (!JBColor.isBright()) {
                JBColor(0xBBBBBB, 0xBBBBBB)
            } else {
                UIUtil.getTextFieldForeground()
            }
        }

        // --- Toggle Button Styling (Minimalist) ---
        picker.componentToggleCalendarButton.apply {
            isContentAreaFilled = false
            border = JBUI.Borders.empty()
            // Optional: Set custom icon for better dark theme integration
            // icon = AllIcons.Actions.Calendar
        }

        return picker
    }

    override fun createCenterPanel(): JComponent? {
        return FormBuilder.createFormBuilder()
            .addComponent(allDataRadio)
            .addComponent(dateRangeRadio)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Start date:"), startDatePicker)
            .addVerticalGap(5)
            .addLabeledComponent(JBLabel("End date:"), endDatePicker)
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        if (dateRangeRadio.isSelected) {
            val start = startDatePicker.date
            val end = endDatePicker.date

            if (start == null) {
                return ValidationInfo("Please select a start date", startDatePicker)
            }
            if (end == null) {
                return ValidationInfo("Please select an end date", endDatePicker)
            }

            if (start.isAfter(end)) {
                return ValidationInfo("Start date must be before end date", startDatePicker)
            }
        }
        return null
    }

    fun isExportAll() = allDataRadio.isSelected

    fun getStartDate(): String {
        return startDatePicker.date.toString()
    }

    fun getEndDate(): String {
        return endDatePicker.date.toString()
    }
}