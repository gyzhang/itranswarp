package com.itranswarp.bean;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;
import com.itranswarp.model.AbstractEntity;

public abstract class AbstractRequestBean {

	private static final Pattern PATTERN_ALIAS = Pattern
			.compile("^[a-z][a-z0-9]{0," + (AbstractEntity.VAR_ENUM - 1) + "}$");
	private static final Pattern PATTERN_TAG = Pattern.compile("^[^\\,\\;]{1," + AbstractEntity.VAR_ENUM + "}$");
	private static final Pattern PATTERN_HASH = Pattern.compile("^[a-f0-9]{64}$");
	
	private long TEXT_LENGTH = 65535L; //MySQL TEXT 字段类型长度
	private long MEDIUMTEXT_LENGTH = 16777215L; //MySQL MEDIUMTEXT 字段类型长度
	private long LONGTEXT_LENGTH = 4294967295L; //MySQL LONGTEXT 字段类型长度

	public abstract void validate(boolean createMode);

	protected String checkPassword(String value) {
		if (value == null || value.length() != AbstractEntity.VAR_CHAR_HASH) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "password", "Invalid password.");
		}
		Matcher matcher = PATTERN_HASH.matcher(value);
		if (!matcher.matches()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "password", "Invalid password.");
		}
		return value;
	}

	protected void checkId(String name, long value) {
		if (value <= 0) {
			throw new ApiException(ApiError.PARAMETER_INVALID, name, "Invalid id.");
		}
	}

	protected void checkTimestamp(String name, long value) {
		if (value < 0) {
			throw new ApiException(ApiError.PARAMETER_INVALID, name, "Invalid timestamp of " + name);
		}
	}

	protected String checkLocalDate(String name, String value) {
		if (value == null || value.isEmpty()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, name, "Invalid date of " + name);
		}
		try {
			return LocalDate.parse(value.trim()).toString();
		} catch (DateTimeParseException e) {
			throw new ApiException(ApiError.PARAMETER_INVALID, name, "Invalid date of " + name);
		}
	}

	protected String checkName(String value) {
		return checkString("name", AbstractEntity.VAR_CHAR_NAME, value);
	}

	protected String checkIcon(String value) {
		return checkString("icon", AbstractEntity.VAR_CHAR_SVG, value);
	}

	protected String checkDescription(String value) {
		return checkString("description", AbstractEntity.VAR_CHAR_DESCRIPTION, value);
	}

	protected String checkContent(String value) {
		return checkString("content", this.MEDIUMTEXT_LENGTH, value); //字段类型修改为 MEDIUMTEXT
	}

	protected String checkImage(String value) {
		return checkString("image", this.LONGTEXT_LENGTH, value); //字段类型修改为 LONGTEXT
	}

	protected String checkAlias(String value) {
		if (value == null) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "alias", "Invalid alias.");
		}
		value = value.trim();
		Matcher matcher = PATTERN_ALIAS.matcher(value);
		if (!matcher.matches()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "alias", "Invalid alias.");
		}
		return value;
	}

	protected String checkTag(String value) {
		if (value == null) {
			return "";
		}
		Matcher matcher = PATTERN_TAG.matcher(value);
		if (!matcher.matches()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "tag", "Invalid tag.");
		}
		return checkString("tag", AbstractEntity.VAR_ENUM, value);
	}

	protected String checkTags(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String[] ss = Arrays.stream(value.strip().replaceAll("\\s+", " ").split("\\s?[\\,\\;]+\\s?")).map(String::strip)
				.filter(s -> !s.isEmpty()).map(this::checkTag).toArray(String[]::new);
		return checkString("tags", AbstractEntity.VAR_CHAR_TAGS, String.join(",", ss));
	}

	protected void checkLong(String name, long value, LongPredicate predicate) {
		if (!predicate.test(value)) {
			throw new ApiException(ApiError.PARAMETER_INVALID, name, "Invalid " + name);
		}
	}

	protected String checkUrl(String url) {
		url = checkString("url", AbstractEntity.VAR_CHAR_URL, url);
		if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("/")) {
			return url;
		}
		throw new ApiException(ApiError.PARAMETER_INVALID, "url", "Invalid URL.");
	}

	/**
	 * 检查字符串
	 * @param paramName
	 * @param maxLength 最大长度，为了存储MEDIUMTEXT和LONGTEXT，将参数类型从 int 修改为 long
	 * @param s
	 * @return
	 */
	private String checkString(String paramName, long maxLength, String s) {
		if (s == null) {
			throw new ApiException(ApiError.PARAMETER_INVALID, paramName,
					"Parameter " + paramName + " must not be null.");
		}
		s = s.strip();
		if (s.isEmpty()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, paramName,
					"Parameter " + paramName + " must not be emtpy.");
		}
		if (s.length() > maxLength) {
			throw new ApiException(ApiError.PARAMETER_INVALID, paramName, "Parameter " + paramName + " is too long.");
		}
		return s;
	}

}
