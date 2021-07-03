package com.mishiranu.dashchan.chan.neochan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.util.Pair;
import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class NeochanChanMarkup extends ChanMarkup {
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_SPOILER | TAG_STRIKE | TAG_CODE;

	public NeochanChanMarkup() {
		addTag("strong", TAG_BOLD);
		addTag("i", TAG_ITALIC);
		addTag("del", TAG_SPOILER);
		addTag("blockquote", TAG_QUOTE);
		addTag("s", TAG_STRIKE);
		addTag("code", TAG_CODE);
		addColorable("span");

		addPreformatted("span", "aa", true);
		addPreformatted("code", true);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		CommentEditor commentEditor = new CommentEditor();
		commentEditor.addTag(TAG_BOLD, "[b]", "[/b]", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_ITALIC, "[i]", "[/i]", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_SPOILER, "[spoiler]", "[/spoiler]", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_STRIKE, "[s]", "[/s]", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_CODE, "[code]", "[/code]", CommentEditor.FLAG_ONE_LINE);
		return commentEditor;
	}

	@Override
	public boolean isTagSupported(String boardName, int tag) {
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) {
			return new Pair<>(matcher.group(1), matcher.group(2));
		}
		return null;
	}
}
