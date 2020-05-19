package com.itranswarp.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.itranswarp.bean.GitbookSummaryBean;

public class GitbookSummaryUtil {

	/**
	 * 从Gitbook的summary文件中读取文章目录结构，不支持文件内页面锚点
	 * 
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<GitbookSummaryBean> readLines(File file) throws FileNotFoundException, IOException {
		try (BufferedReader bf = new BufferedReader(new FileReader(file))) {
			List<GitbookSummaryBean> summary = new ArrayList<GitbookSummaryBean>();
			String line;
			int[] displayOrders = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };// 最多支持10级目录，第0级不用
			GitbookSummaryBean[] parents = new GitbookSummaryBean[] { null, null, null, null, null, null, null, null, null, null, null };// 最多支持10级目录，第0级不用
			int preLineLevel = 1;// 前一行的级别
			int curLineLevel = 1;// 当前行的级别
			// 按行读取字符串
			while ((line = bf.readLine()) != null) {
				String tempLine = line;
				if (!tempLine.trim().startsWith("* [")) {// 跳过不是目录的行
					continue;
				}
				GitbookSummaryBean bean = new GitbookSummaryBean();
				bean.setContent(line);
				bean.setTitle(line.substring(line.indexOf("* [") + 3, line.indexOf("](")));
				bean.setMarkdownFile(line.substring(line.indexOf("](") + 2, line.lastIndexOf(")")));
				// 解析line到bean：“8个空格* [1.2.1 在路上](第01章 万事开头难/1.2.1onTheWay.md)”
				if (line.startsWith("* [")) {// 一级内容，如“* [第01章 万事开头难](第01章 万事开头难/Start.md)”
					curLineLevel = 1;
				} else {// 非一级内容，用空格解析
					String s = line.substring(0, line.indexOf("* ["));// 左边的空格
					curLineLevel = s.length() / 4 + 1;
				}
				bean.setLevel(curLineLevel);// 当前目录行级别
				bean.setParent(parents[curLineLevel]);// 当前页面的父页面
				parents[curLineLevel + 1] = bean; // 当前页面就是后续下级页面的父页面

				if (curLineLevel == preLineLevel) {// 同级
					bean.setDisplayOrder(displayOrders[curLineLevel]);
					displayOrders[curLineLevel] = displayOrders[curLineLevel] + 1;// 当前编号+1，为下一行做准备
				}
				if (curLineLevel > preLineLevel) {// 向下降级
					bean.setDisplayOrder(0);// 重新编号
					displayOrders[curLineLevel] = displayOrders[curLineLevel] + 1;// 当前编号+1，为下一行做准备
				}
				if (curLineLevel < preLineLevel) {// 向上升级，沿用既有编号
					for (int i = curLineLevel; i < displayOrders.length - 1; i++) {// 将当前级以下的全部置0，重新编号
						displayOrders[i + 1] = 0;
						parents[curLineLevel] = null;
					}
					bean.setDisplayOrder(displayOrders[curLineLevel]);
				}

				summary.add(bean);
				preLineLevel = curLineLevel;// 下一行的前一行就是当前行
			}
			return summary;
		}
	}

}
