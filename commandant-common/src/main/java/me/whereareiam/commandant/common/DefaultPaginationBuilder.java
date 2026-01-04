package me.whereareiam.commandant.common;

import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.model.message.PaginationMessages;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of PaginationBuilder.
 * Formats navigation elements (previous/next buttons) and page indicators.
 * <p>
 * This builder is generic and can be used across different projects by providing
 * a PaginationMessages configuration.
 * <p>
 * Usage example:
 * <pre>{@code
 * PaginationMessages config = ...; // from your config
 * PaginationBuilder builder = new DefaultPaginationBuilder(config);
 *
 * String message = "Header\n<commands>\n<pagination>";
 * String result = builder.build(message, 25, 2, 10);
 * // Result: "Header\n<commands>\n← Page 2/3 →"
 * }</pre>
 */
public class DefaultPaginationBuilder implements PaginationBuilder {
	private final PaginationMessages messages;
	private final SerializerOptions.PlaceholderFormat placeholderFormat;

	/**
	 * Creates a new DefaultPaginationBuilder with the given configuration.
	 *
	 * @param messages The pagination message configuration
	 */
	public DefaultPaginationBuilder(@NotNull PaginationMessages messages) {
		this(messages, SerializerOptions.PlaceholderFormat.CURLY_BRACES);
	}

	public DefaultPaginationBuilder(
			@NotNull PaginationMessages messages,
			@NotNull SerializerOptions.PlaceholderFormat placeholderFormat
	) {
		this.messages = messages;
		this.placeholderFormat = placeholderFormat;
	}

	@Override
	@NotNull
	public String build(@NotNull String message, int totalItems, int currentPage, int itemsPerPage) {
		int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

		if (totalPages <= 1 && !messages.isShowPaginationIfOnePage()) {
			return replaceToken(message, "pagination", "");
		}

		String pagination = formatPagination(currentPage, totalPages);
		return replaceToken(message, "pagination", pagination);
	}

	/**
	 * Formats the pagination controls based on current page and total pages.
	 *
	 * @param currentPage Current page number (1-indexed)
	 * @param totalPages  Total number of pages
	 * @return Formatted pagination string
	 */
	@NotNull
	private String formatPagination(int currentPage, int totalPages) {
		String previousLink = buildPreviousLink(currentPage);
		String nextLink = buildNextLink(currentPage, totalPages);

		return replaceToken(
				replaceToken(
						replaceToken(
								replaceToken(messages.getFormat(), "previous", previousLink),
								"next",
								nextLink
						),
						"current",
						String.valueOf(currentPage)
				),
				"max",
				String.valueOf(totalPages)
		);
	}

	/**
	 * Builds the previous page navigation link.
	 *
	 * @param currentPage Current page number
	 * @return Formatted previous link, or empty string if not applicable
	 */
	@NotNull
	private String buildPreviousLink(int currentPage) {
		if (currentPage > 1) {
			return replaceToken(
					messages.getPreviousTagFormat(),
					"previousPage",
					String.valueOf(currentPage - 1)
			);
		}

		return messages.isShowPreviousEvenIfFirst()
				? replaceToken(messages.getPreviousTagFormat(), "previousPage", "1")
				: "";
	}

	/**
	 * Builds the next page navigation link.
	 *
	 * @param currentPage Current page number
	 * @param totalPages  Total number of pages
	 * @return Formatted next link, or empty string if not applicable
	 */
	@NotNull
	private String buildNextLink(int currentPage, int totalPages) {
		if (currentPage < totalPages) {
			return replaceToken(
					messages.getNextTagFormat(),
					"nextPage",
					String.valueOf(currentPage + 1)
			);
		}

		return messages.isShowNextEvenIfLast()
				? replaceToken(messages.getNextTagFormat(), "nextPage", String.valueOf(totalPages))
				: "";
	}

	@NotNull
	private String replaceToken(@NotNull String message, @NotNull String placeholder, @NotNull String value) {
		return message.replace(placeholderFormat.format(placeholder), value);
	}
}


