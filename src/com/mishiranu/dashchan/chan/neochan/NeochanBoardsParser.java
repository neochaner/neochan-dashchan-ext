package com.mishiranu.dashchan.chan.neochan;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class NeochanBoardsParser {
	private final String source;

	private String category;
	private final LinkedHashMap<String, String> boards = new LinkedHashMap<>();

	private static final Pattern PATTERN_LINK = Pattern.compile("/(.*?)/");

	public NeochanBoardsParser(String source) {
		this.source = source;
	}

	public LinkedHashMap<String, String> convertMap() throws ParseException {
		PARSER.parse(source, this);
		return boards;
	}

	private static final TemplateParser<NeochanBoardsParser> PARSER = TemplateParser.<NeochanBoardsParser>builder()
			.name("li").open((i, h, t, a) -> a.get("title") == null).content((instance, holder, text) -> {
		holder.category = StringUtils.clearHtml(text);
	}).name("a").open((instance, holder, tagName, attributes) -> {
		if (holder.category != null) {
			String link = attributes.get("href");
			Matcher matcher = PATTERN_LINK.matcher(link);
			if (matcher.matches()) {
				holder.boards.put(matcher.group(1), holder.category);
			}
		}
		return false;
	}).name("div").close((instance, holder, tagName) -> {
		if (holder.category != null) {
			instance.finish();
		}
	}).prepare();
}
