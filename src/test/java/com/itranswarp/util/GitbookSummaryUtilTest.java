package com.itranswarp.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.itranswarp.bean.GitbookSummaryBean;

public class GitbookSummaryUtilTest {
	@Test
	void testGitbookSummary() throws IOException {
		String fileName = "/Users/kevin/temp/demobook/SUMMARY.md";
		List<GitbookSummaryBean> list = GitbookSummaryUtil.readLines(new File(fileName));
		for (GitbookSummaryBean bean: list) {
			GitbookSummaryBean parent = bean.getParent();
			String parentTitle = "";
			if (parent != null) {
				parentTitle = parent.getTitle();
			}
			System.out.println("父：" + parentTitle + ", 级别："+bean.getLevel() + ", 序号：" + bean.getDisplayOrder() + ", 标题：" + bean.getTitle() + ", 文件：" + bean.getMarkdownFile() + ", 行：" + bean.getContent());
		}
	}

}
