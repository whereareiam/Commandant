package me.whereareiam.commandant;

import me.whereareiam.commandant.builder.HelpBuilder;
import me.whereareiam.commandant.builder.PaginationBuilder;
import me.whereareiam.commandant.common.DefaultHelpBuilder;
import me.whereareiam.commandant.model.message.HelpMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Static helper for creating HelpBuilder instances.
 * Provides a clean API for help command functionality.
 */
@SuppressWarnings("unused")
public final class Help {
	/**
	 * Creates a HelpBuilder with the given configuration.
	 *
	 * @param helpMessages        The help message configuration
	 * @param customArgumentNames Optional map of argument names to custom display names
	 * @param paginationBuilder   Optional pagination builder for multi-page help
	 * @param itemsPerPage        Number of commands to display per page
	 * @param <S>                 The sender type
	 * @return A new HelpBuilder instance
	 */
	@NotNull
	public static <S> HelpBuilder<S> create(
			@NotNull HelpMessages helpMessages,
			@Nullable Map<String, String> customArgumentNames,
			@Nullable PaginationBuilder paginationBuilder,
			int itemsPerPage
	) {
		return new DefaultHelpBuilder<>(helpMessages, customArgumentNames, paginationBuilder, itemsPerPage);
	}

	/**
	 * Creates a HelpBuilder without pagination.
	 *
	 * @param helpMessages        The help message configuration
	 * @param customArgumentNames Optional map of argument names to custom display names
	 * @param itemsPerPage        Number of commands to display per page
	 * @param <S>                 The sender type
	 * @return A new HelpBuilder instance
	 */
	@NotNull
	public static <S> HelpBuilder<S> create(
			@NotNull HelpMessages helpMessages,
			@Nullable Map<String, String> customArgumentNames,
			int itemsPerPage
	) {
		return new DefaultHelpBuilder<>(helpMessages, customArgumentNames, null, itemsPerPage);
	}

	/**
	 * Creates a HelpBuilder with minimal configuration.
	 *
	 * @param helpMessages The help message configuration
	 * @param itemsPerPage Number of commands to display per page
	 * @param <S>          The sender type
	 * @return A new HelpBuilder instance
	 */
	@NotNull
	public static <S> HelpBuilder<S> create(@NotNull HelpMessages helpMessages, int itemsPerPage) {
		return new DefaultHelpBuilder<>(helpMessages, null, null, itemsPerPage);
	}
}
