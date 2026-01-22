package me.whereareiam.commandant;

import me.whereareiam.commandant.builder.HelpBuilder;
import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.common.DefaultHelpBuilder;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.commandant.model.message.PaginationMessages;
import me.whereareiam.keystone.model.SerializerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Fluent helper for creating HelpBuilder instances.
 */
@SuppressWarnings("unused")
public final class Help {
	/**
	 * Creates a fluent builder for HelpBuilder configuration.
	 *
	 * @param helpMessages The help message configuration
	 * @param <S>          The sender type
	 * @return A new Help builder
	 */
	@NotNull
	public static <S> Builder<S> builder(@NotNull HelpMessages helpMessages) {
		return new Builder<>(helpMessages);
	}

	/**
	 * Fluent builder for HelpBuilder configuration.
	 *
	 * @param <S> The sender type
	 */
	public static final class Builder<S> {
		private final HelpMessages helpMessages;
		private Map<String, String> customArgumentNames;
		private PaginationBuilder paginationBuilder;
		private PaginationMessages paginationMessages;
		private int itemsPerPage;
		private boolean sortAlphabetically;
		private boolean dedupeByDefinitionId;
		private SerializerOptions.PlaceholderFormat placeholderFormat = SerializerOptions.PlaceholderFormat.CURLY_BRACES;

		private Builder(@NotNull HelpMessages helpMessages) {
			this.helpMessages = helpMessages;
			this.itemsPerPage = helpMessages.getCommandsPerPage();
		}

		@NotNull
		public Builder<S> customArgumentNames(@Nullable Map<String, String> customArgumentNames) {
			this.customArgumentNames = customArgumentNames;
			return this;
		}

		@NotNull
		public Builder<S> paginationBuilder(@Nullable PaginationBuilder paginationBuilder) {
			this.paginationBuilder = paginationBuilder;
			return this;
		}

		@NotNull
		public Builder<S> paginationMessages(@Nullable PaginationMessages paginationMessages) {
			this.paginationMessages = paginationMessages;
			return this;
		}

		@NotNull
		public Builder<S> itemsPerPage(int itemsPerPage) {
			this.itemsPerPage = itemsPerPage;
			return this;
		}

		@NotNull
		public Builder<S> sortAlphabetically(boolean sortAlphabetically) {
			this.sortAlphabetically = sortAlphabetically;
			return this;
		}

		@NotNull
		public Builder<S> dedupeByDefinitionId(boolean dedupeByDefinitionId) {
			this.dedupeByDefinitionId = dedupeByDefinitionId;
			return this;
		}

		@NotNull
		public Builder<S> placeholderFormat(@NotNull SerializerOptions.PlaceholderFormat placeholderFormat) {
			this.placeholderFormat = placeholderFormat;
			return this;
		}

		@NotNull
		public HelpBuilder<S> build() {
			PaginationBuilder resolvedPaginationBuilder = paginationBuilder;
			if (resolvedPaginationBuilder == null && paginationMessages != null) {
				resolvedPaginationBuilder = Pagination.builder(paginationMessages)
						.placeholderFormat(placeholderFormat)
						.build();
			}

			return new DefaultHelpBuilder<>(
					helpMessages,
					customArgumentNames,
					resolvedPaginationBuilder,
					itemsPerPage,
					sortAlphabetically,
					dedupeByDefinitionId,
					placeholderFormat
			);
		}
	}
}
