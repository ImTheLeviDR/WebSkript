package ch.njol.skript.expressions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.log.LogEntry;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.util.Kleenean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Name("Script Load Errors")
@Description("""
	The errors from the most recent 'load/reload script ... does (not) have errors' condition.
	Can be looped over or stored in a list variable.""")
@Example("""
	if reload script "https://example.com/test.sk" does not have errors:
		send "Successfully reloaded."
	else:
		set {_errors::*} to errors
		loop {_errors::*}:
			send "&c%loop-value%"
	""")
@Since("2.10")
public class ExprScriptErrors extends SimpleExpression<String> {

	public static final ThreadLocal<List<String>> lastErrors = ThreadLocal.withInitial(Collections::emptyList);

	static {
		Skript.registerExpression(ExprScriptErrors.class, String.class, ExpressionType.SIMPLE, "[the] [last] [(script|load|reload)] error[s]");
	}

	public static void storeFrom(RetainingLogHandler logHandler) {
		List<String> errors = new ArrayList<>();
		for (LogEntry entry : logHandler.getErrors())
			errors.add(entry.getMessage());
		lastErrors.set(errors);
	}

	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
		return true;
	}

	@Override
	protected String[] get(final Event e) {
		return lastErrors.get().toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		return "the last script errors";
	}
}
