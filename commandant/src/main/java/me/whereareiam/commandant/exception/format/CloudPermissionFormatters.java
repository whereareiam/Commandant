package me.whereareiam.commandant.exception.format;

import org.incendo.cloud.permission.AndPermission;
import org.incendo.cloud.permission.OrPermission;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Built-in formatter helpers for Cloud {@link Permission} values.
 */
@SuppressWarnings("unused")
public final class CloudPermissionFormatters {
	private static final ExceptionFormatter<Permission> RAW = Permission::permissionString;
	private static final ExceptionFormatter<Permission> MINIMAL = permission -> format(permission, ParentOperator.NONE);
	private static final Comparator<String> SEGMENT_COMPARATOR = Comparator
			.comparingInt(CloudPermissionFormatters::sortRank)
			.thenComparing(CloudPermissionFormatters::sortKey)
			.thenComparing(s -> s);

	/**
	 * Returns a formatter that preserves Cloud's raw {@link Permission#permissionString()} output.
	 *
	 * @return raw permission formatter
	 */
	public static @NotNull ExceptionFormatter<Permission> raw() {
		return RAW;
	}

	/**
	 * Returns a formatter that removes unnecessary wrapper parentheses while preserving mixed
	 * {@code AND}/{@code OR} grouping semantics.
	 *
	 * @return minimal permission formatter
	 */
	public static @NotNull ExceptionFormatter<Permission> minimal() {
		return MINIMAL;
	}

	private static @NotNull String format(
			@NotNull Permission permission,
			@NotNull ParentOperator parentOperator
	) {
		if (permission instanceof AndPermission andPermission)
			return join(andPermission.permissions(), ParentOperator.AND, " & ", parentOperator);

		if (permission instanceof OrPermission orPermission)
			return join(orPermission.permissions(), ParentOperator.OR, " | ", parentOperator);

		return permission.permissionString();
	}

	private static @NotNull String join(
			@NotNull Collection<@NotNull Permission> permissions,
			@NotNull ParentOperator currentOperator,
			@NotNull String delimiter,
			@NotNull ParentOperator parentOperator
	) {
		List<String> segments = new ArrayList<>(permissions.size());
		for (Permission permission : permissions)
			segments.add(format(permission, currentOperator));

		segments.sort(SEGMENT_COMPARATOR);
		String rendered = String.join(delimiter, segments);
		if (needsGrouping(parentOperator, currentOperator))
			return "(" + rendered + ")";

		return rendered;
	}

	private static boolean needsGrouping(
			@NotNull ParentOperator parentOperator,
			@NotNull ParentOperator currentOperator
	) {
		return parentOperator != ParentOperator.NONE && parentOperator != currentOperator;
	}

	private static int sortRank(@NotNull String segment) {
		return isGrouped(segment) ? 1 : 0;
	}

	private static @NotNull String sortKey(@NotNull String segment) {
		if (!isGrouped(segment)) return segment;
		return segment.substring(1, segment.length() - 1);
	}

	private static boolean isGrouped(@NotNull String segment) {
		return segment.length() >= 2 && segment.charAt(0) == '(' && segment.charAt(segment.length() - 1) == ')';
	}

	private enum ParentOperator {
		NONE,
		AND,
		OR
	}
}
