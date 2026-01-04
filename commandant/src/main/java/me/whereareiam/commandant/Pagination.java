package me.whereareiam.commandant;

import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.common.DefaultPaginationBuilder;
import me.whereareiam.commandant.model.message.PaginationMessages;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * Fluent helper for creating PaginationBuilder instances.
 */
@SuppressWarnings("unused")
public final class Pagination {
	/**
	 * Creates a fluent builder for PaginationBuilder configuration.
	 *
	 * @param paginationMessages The pagination message configuration
	 * @return A new Pagination builder
	 */
	@NotNull
	public static Builder builder(@NotNull PaginationMessages paginationMessages) {
		return new Builder(paginationMessages);
	}

	/**
	 * Fluent builder for PaginationBuilder configuration.
	 */
	public static final class Builder {
		private final PaginationMessages paginationMessages;
		private SerializerOptions.PlaceholderFormat placeholderFormat = SerializerOptions.PlaceholderFormat.CURLY_BRACES;

		private Builder(@NotNull PaginationMessages paginationMessages) {
			this.paginationMessages = paginationMessages;
		}

		@NotNull
		public Builder placeholderFormat(@NotNull SerializerOptions.PlaceholderFormat placeholderFormat) {
			this.placeholderFormat = placeholderFormat;
			return this;
		}

		@NotNull
		public PaginationBuilder build() {
			return new DefaultPaginationBuilder(paginationMessages, placeholderFormat);
		}
	}
}
