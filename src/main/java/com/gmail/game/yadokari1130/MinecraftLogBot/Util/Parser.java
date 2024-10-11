package com.gmail.game.yadokari1130.MinecraftLogBot.Util;

import com.gmail.game.yadokari1130.MinecraftLogBot.Data.LogFilterJson;
import com.gmail.game.yadokari1130.MinecraftLogBot.Data.Option;
import com.gmail.game.yadokari1130.MinecraftLogBot.Data.Player;
import com.gmail.game.yadokari1130.MinecraftLogBot.MinecraftLog.Log;
import com.gmail.game.yadokari1130.MinecraftLogBot.Data.LogFilter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {
	private static final List<LogFilter> logFilters = new ArrayList<>();
	private static final Map<String, Pattern> patterns = new HashMap<String, Pattern>();
	private static final Map<String, String> names = new HashMap<String, String>();
	private static final Map<String, String> translations = new HashMap<String, String>();

	public static void register(LogFilterJson json) {
		if (json.isPlayerLog() && !json.getRegex().contains("?<player>")) {
			System.out.println(json.isWhite() ? "ホワイト" : "ブラック" + "リスト「" + json.getRegex() + "」の登録に失敗しました");
			System.out.println("正規表現に名前付きグループ<?player>を含めてください");
			return;
		}
		for (int i = 0; i < json.getArgCount(); i++) {
			if (!json.getRegex().contains("?<arg" + i + ">")) {
				System.out.println(json.isWhite() ? "ホワイト" : "ブラック" + "リスト「" + json.getRegex() + "」の登録に失敗しました");
				System.out.println("正規表現に名前付きグループ<?arg" + i + ">を含めてください");
				return;
			}
		}

		if (json.getCommands().size() != json.getIsReturnOutputs().size() || json.getIsReturnOutputs().size() != json.getIsReturnErrors().size()) {
			System.out.println(json.isWhite() ? "ホワイト" : "ブラック" + "リスト「" + json.getRegex() + "」の登録に失敗しました");
			System.out.println("commands、isReturnOutputs、isReturnErrorsの数が一致していません");
			return;
		}

		List<List<String>> commandList = new ArrayList<>();
		for (String c : json.getCommands()) {
			List<String> command = new ArrayList<>();
			for (String s : c.split("\\s")) {
				if (s.isEmpty()) continue;
				command.add(s);
			}
			commandList.add(command);
		}
		List<Set<Option>> options = new ArrayList<>();
		for (int i = 0; i < json.getIsReturnOutputs().size(); i++) {
			Set<Option> option = new HashSet<>();
			if (json.getIsReturnOutputs().get(i)) option.add(Option.OUTPUT);
			if (json.getIsReturnErrors().get(i)) option.add(Option.ERROR);
			options.add(option);
		}

		LogFilter logFilter = new LogFilter(json.getRegex(), json.getSeparator(), commandList, options, json.getArgCount(), json.isWhite(), json.isPlayerLog());
		logFilters.add(logFilter);
	}

	public static void register(List<LogFilterJson> jsons) {
		for (LogFilterJson j : jsons) {
			register(j);
		}
	}

	public static void addPattern(String label, Pattern pattern) {
		patterns.put(label, pattern);
	}

	public static void addNames(Map<String, String> entry) {
		names.putAll(entry);
	}

	public static void addTranslations(Map<String, String> entry) {
		translations.putAll(entry);
	}

	/**
	 * ログのパース
	 *
	 * @param line ログ
	 * @return Log textsがnullならblacklist　上の方が優先度が高い
	 */
	public static Log parse(String line) {
		Log result = null;

		for (Map.Entry<String, Pattern> entry: patterns.entrySet()) {
			Matcher m = entry.getValue().matcher(line);
			if (!m.matches()) continue;

			if (translations.containsKey(entry.getKey()))
			{
				List<String> groups = new ArrayList<String>();
				for (int i = 1; i < m.groupCount() + 1; i++) {
					String text = m.group("g" + i);
					for (Map.Entry<String, String> name: names.entrySet()) {
						if (text.equals(name.getValue()) || text.equals("[" + name.getValue() + "]"))
						{
							if (translations.containsKey(name.getKey()))
							{
								text = text.replace(name.getValue(), translations.get(name.getKey()));
							}

							break;
						}
					}

					groups.add(text);
				}
				String output = String.format(translations.get(entry.getKey()), groups.toArray());

				result = new Log(output, new ArrayList<List<String>>(), null, null, null);
				return result;
			}

			result = new Log(line, new ArrayList<List<String>>(), null, null, null);
			return result;
		}

		for (int i = 0; i < logFilters.size(); i++) {
			LogFilter lf = logFilters.get(i);
			Matcher m = logFilters.get(i).getMatcher(line);

			if (m.matches()) {
				Player player = null;
				List<Integer> ignoreStarts = new ArrayList<>();
				List<Integer> ignoreEnds = new ArrayList<>();
				if (lf.isPlayerLog()) {
					player = new Player(m.group("player"));
					ignoreStarts.add(m.start("player"));
					ignoreEnds.add(m.end("player"));
				}

				List<String> args = new ArrayList<>();
				for (int k = 0; k < lf.getArgCount(); k++) {
					args.add(m.group("arg" + k));
					ignoreStarts.add(m.start("arg" + k));
					ignoreEnds.add(m.end("arg" + k));
				}

				if (!lf.isWhite()) {
					result = new Log(null, lf.getCommands(), lf.getOptions(), args, player);
					break;
				}
				if (result == null) {
					List<String> texts = new ArrayList<>();
					for (int k = 1; k <= m.groupCount(); k++) {
						boolean ignore = false;
						for (int n = 0; n < ignoreStarts.size(); n++) {
							if (ignoreStarts.get(n) == m.start(k) && ignoreEnds.get(n) == m.end(k)) {
								ignore = true;
								break;
							}
						}

						if (!ignore) texts.add(m.group(k));
					}
					result = new Log(String.join(lf.getSeparator(), texts), lf.getCommands(), lf.getOptions(), args, player);
				}
			}
		}

		return result;
	}
}
