package me.whereareiam.commandant.model.message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Configuration model for pagination-related message and behavior.
 * Used by PaginationBuilder to format paginated command outputs.
 */
@Getter
@Setter
@ToString
public class PaginationMessages {
	/**
	 * Whether to show pagination when there's only one page.
	 */
	private boolean showPaginationIfOnePage;

	/**
	 * Format template for pagination display.
	 * Placeholders (format defined by caller):
	 * - previous: Previous page navigation
	 * - next: Next page navigation
	 * - current: Current page number
	 * - max: Total number of pages
	 * <p>
	 * Example: "<previous> <gray>Page <current>/<max></gray> <next>"
	 */
	private String format;

	/**
	 * Whether to show previous page button even on the first page.
	 */
	private boolean showPreviousEvenIfFirst;

	/**
	 * Format template for previous page button.
	 * Placeholder: previousPage - the previous page number
	 * <p>
	 * Example: "<hover:show_text:'Go to page <previousPage>'><click:run_command:'/help <previousPage>'><gold>←</gold></click></hover>"
	 */
	private String previousTagFormat;

	/**
	 * Whether to show next page button even on the last page.
	 */
	private boolean showNextEvenIfLast;

	/**
	 * Format template for next page button.
	 * Placeholder: nextPage - the next page number
	 * <p>
	 * Example: "<hover:show_text:'Go to page <nextPage>'><click:run_command:'/help <nextPage>'><gold>→</gold></click></hover>"
	 */
	private String nextTagFormat;
}
