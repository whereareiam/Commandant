package me.whereareiam.commandant.exception.format;

import org.incendo.cloud.permission.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudPermissionFormattersTest {
	@Test
	void rawFormatterPreservesCloudPermissionString() {
		Permission permission = Permission.allOf(
				Permission.permission("identica.admin"),
				Permission.permission("identica.staff")
		);

		assertEquals(permission.permissionString(), CloudPermissionFormatters.raw().format(permission));
	}

	@Test
	void minimalFormatterKeepsSimplePermission() {
		assertEquals(
				"identica.admin",
				CloudPermissionFormatters.minimal().format(Permission.permission("identica.admin"))
		);
	}

	@Test
	void minimalFormatterRendersAndPermissionWithoutWrapperNoise() {
		Permission permission = Permission.allOf(
				Permission.permission("identica.admin"),
				Permission.permission("identica.staff")
		);

		assertEquals("identica.admin & identica.staff", CloudPermissionFormatters.minimal().format(permission));
	}

	@Test
	void minimalFormatterRendersOrPermissionWithoutWrapperNoise() {
		Permission permission = Permission.anyOf(
				Permission.permission("identica.admin"),
				Permission.permission("identica.staff")
		);

		assertEquals("identica.admin | identica.staff", CloudPermissionFormatters.minimal().format(permission));
	}

	@Test
	void minimalFormatterAddsGroupingForMixedOperators() {
		Permission permission = Permission.anyOf(
				Permission.permission("identica.admin"),
				Permission.allOf(
						Permission.permission("identica.staff"),
						Permission.permission("identica.audit")
				)
		);

		assertEquals(
				"identica.admin | (identica.audit & identica.staff)",
				CloudPermissionFormatters.minimal().format(permission)
		);
	}

	@Test
	void minimalFormatterHandlesEmptyPermission() {
		assertEquals("", CloudPermissionFormatters.minimal().format(Permission.empty()));
	}
}
