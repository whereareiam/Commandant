package me.whereareiam.commandant.exception.format;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of value formatters used by exception handlers when populating message placeholders.
 * <p>
 * Formatters are selected by value type. When no registered formatter matches a runtime value,
 * the caller may fall back to its default string rendering.
 * <p>
 * Example usage:
 * <pre>{@code
 * ExceptionFormatting formatting = ExceptionFormatting.builder()
 *     .format(
 *         org.incendo.cloud.permission.Permission.class,
 *         CloudPermissionFormatters.minimal()
 *     )
 *     .build();
 * }</pre>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionFormatting {
	private final Map<Class<?>, ExceptionFormatter<?>> formatters;

	/**
	 * Creates an empty formatting registry.
	 *
	 * @return empty exception formatting
	 */
	public static @NotNull ExceptionFormatting defaults() {
		return new ExceptionFormatting(Map.of());
	}

	/**
	 * Creates a new builder.
	 *
	 * @return builder
	 */
	public static @NotNull Builder builder() {
		return new Builder();
	}

	/**
	 * Attempts to format the given value using the most specific matching registered formatter.
	 *
	 * @param value value to format
	 * @return formatted text, or {@code null} if no formatter matches
	 */
	public @Nullable String format(@Nullable Object value) {
		if (value == null) return null;

		for (Map.Entry<Class<?>, ExceptionFormatter<?>> entry : formatters.entrySet()) {
			if (!entry.getKey().isInstance(value)) continue;
			return apply(entry.getValue(), value);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private static @NotNull String apply(@NotNull ExceptionFormatter<?> formatter, @NotNull Object value) {
		return ((ExceptionFormatter<Object>) formatter).format(value);
	}

	public static final class Builder {
		private final LinkedHashMap<Class<?>, ExceptionFormatter<?>> formatters = new LinkedHashMap<>();

		/**
		 * Registers a formatter for the given type.
		 *
		 * @param type value type
		 * @param formatter formatter to use for matching values
		 * @param <T> value type
		 * @return this builder
		 */
		public <T> @NotNull Builder format(
				@NotNull Class<T> type,
				@NotNull ExceptionFormatter<? super T> formatter
		) {
			formatters.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(formatter, "formatter"));
			return this;
		}

		/**
		 * Builds an immutable formatting registry.
		 *
		 * @return exception formatting
		 */
		public @NotNull ExceptionFormatting build() {
			return new ExceptionFormatting(Map.copyOf(formatters));
		}
	}
}
