package me.whereareiam.commandant.builder;

import org.jetbrains.annotations.NotNull;

/**
 * Builder interface for creating pagination controls in command outputs.
 * Formats navigation elements (previous/next buttons) and page indicators.
 */
public interface PaginationBuilder {
	/**
	 * Builds pagination by replacing the {pagination} placeholder in the message.
	 *
	 * @param message      The message containing {pagination} placeholder
	 * @param totalItems   Total number of items to paginate
	 * @param currentPage  Current page number (1-indexed)
	 * @param itemsPerPage Number of items per page
	 * @return The message with {pagination} replaced by formatted pagination controls
	 */
	@NotNull
	String build(@NotNull String message, int totalItems, int currentPage, int itemsPerPage);
}

