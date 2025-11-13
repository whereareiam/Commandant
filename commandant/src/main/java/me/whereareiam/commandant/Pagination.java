package me.whereareiam.commandant;

import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.common.DefaultPaginationBuilder;
import me.whereareiam.commandant.model.message.PaginationMessages;
import org.jetbrains.annotations.NotNull;

/**
 * Static helper for creating PaginationBuilder instances.
 * Provides a clean API for pagination functionality.
 */
@SuppressWarnings("unused")
public final class Pagination {
	/**
	 * Creates a PaginationBuilder with the given configuration.
	 *
	 * @param paginationMessages The pagination message configuration
	 * @return A new PaginationBuilder instance
	 */
	@NotNull
	public static PaginationBuilder create(@NotNull PaginationMessages paginationMessages) {
		return new DefaultPaginationBuilder(paginationMessages);
	}
}
