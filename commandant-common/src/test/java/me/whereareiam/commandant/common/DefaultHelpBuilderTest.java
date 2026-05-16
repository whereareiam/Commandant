package me.whereareiam.commandant.common;

import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.commandant.builder.HelpBuilder;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.commandant.model.message.PaginationMessages;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.description.CommandDescription;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultHelpBuilderTest {
	private CommandManager<TestSender> commandManager;

	@BeforeEach
	void setUp() {
		commandManager = new CommandManager<>(
				ExecutionCoordinator.simpleCoordinator(),
				CommandRegistrationHandler.nullCommandRegistrationHandler()
		) {
			@Override
			public boolean hasPermission(TestSender sender, String permission) {
				return true;
			}
		};
	}

	@Test
	void dedupedCommandsDrivePaginationTotals() {
		Collection<Command<TestSender>> commands = List.of(
				command("alpha", "alpha"),
				command("alphaalias", "alpha"),
				command("bravo", "bravo"),
				command("bravoalias", "bravo"),
				command("charlie", "charlie"),
				command("charliealias", "charlie"),
				command("delta", "delta"),
				command("deltaalias", "delta"),
				command("echo", "echo"),
				command("echoalias", "echo"),
				command("foxtrot", "foxtrot"),
				command("foxtrotalias", "foxtrot"),
				command("golf", "golf"),
				command("golfalias", "golf")
		);

		HelpBuilder<TestSender> builder = new DefaultHelpBuilder<>(
				helpMessages(),
				Map.of(),
				new DefaultPaginationBuilder(paginationMessages()),
				3,
				true,
				true,
				me.whereareiam.keystone.model.SerializerOptions.PlaceholderFormat.CURLY_BRACES
		);

		String secondPage = builder.build(commands, 2);
		String thirdPage = builder.build(commands, 3);

		assertTrue(secondPage.contains("[2/3]"));
		assertTrue(thirdPage.contains("[3/3]"));
		assertTrue(thirdPage.contains("/golf"));
		assertFalse(thirdPage.contains("[3/5]"));
	}

	private Command<TestSender> command(String alias, String definitionId) {
		return commandManager.commandBuilder(alias)
				.meta(CommandantKeys.DEFINITION_ID, definitionId)
				.commandDescription(CommandDescription.commandDescription(definitionId))
				.handler(ctx -> {})
				.build();
	}

	private HelpMessages helpMessages() {
		HelpMessages helpMessages = new HelpMessages();
		helpMessages.setFormat(List.of(
				"Help",
				"{commands}",
				"{pagination}"
		));
		helpMessages.setCommandFormat("/{command} - {description}");
		helpMessages.setNoCommands("No commands");
		helpMessages.setCommandsPerPage(3);
		return helpMessages;
	}

	private PaginationMessages paginationMessages() {
		PaginationMessages paginationMessages = new PaginationMessages();
		paginationMessages.setShowPaginationIfOnePage(false);
		paginationMessages.setFormat("[{current}/{max}]");
		paginationMessages.setShowPreviousEvenIfFirst(false);
		paginationMessages.setPreviousTagFormat("<{previousPage}");
		paginationMessages.setShowNextEvenIfLast(false);
		paginationMessages.setNextTagFormat(">{nextPage}");
		return paginationMessages;
	}

	private static final class TestSender {
	}
}
