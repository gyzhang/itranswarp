package com.itranswarp.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.itranswarp.bean.ArticleBean;
import com.itranswarp.bean.AttachmentBean;
import com.itranswarp.bean.CategoryBean;
import com.itranswarp.bean.MarkdownImageBean;
import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;
import com.itranswarp.enums.Role;
import com.itranswarp.model.Article;
import com.itranswarp.model.Attachment;
import com.itranswarp.model.Category;
import com.itranswarp.model.User;
import com.itranswarp.util.IdUtil;
import com.itranswarp.util.MarkdownFileUtil;
import com.itranswarp.warpdb.PagedResults;
import com.itranswarp.web.filter.HttpContext;

@Component
public class ArticleService extends AbstractService<Article> {

	@Autowired
	TextService textService;

	@Autowired
	AttachmentService attachmentService;

	@Autowired
	ViewService viewService;
	
	@Autowired
	SettingService settingService;

	static final String KEY_RECENT_ARTICLES = "__recent_articles__";
	static final String KEY_ARTICLES_FIRST_PAGE = "__articles__";

	static final String KEY_CATEGORIES = "__categories__";

	static final long KEY_TIMEOUT = 3600;

	public Category getCategoryFromCache(Long id) {
		Category c = this.redisService.hget(KEY_CATEGORIES, id, Category.class);
		if (c == null) {
			c = getCategoryById(id);
			this.redisService.hset(KEY_CATEGORIES, id, c);
		}
		return c;
	}

	public void deleteCategoriesFromCache() {
		this.redisService.del(KEY_CATEGORIES);
	}

	public void deleteArticlesFromCache(long categoryId) {
		this.redisService.del(KEY_ARTICLES_FIRST_PAGE + categoryId);
		this.redisService.del(KEY_RECENT_ARTICLES);
	}

	public void deleteCategoryFromCache(Long id) {
		this.redisService.hdel(KEY_CATEGORIES, id);
	}

	public List<Category> getCategories() {
		return this.db.from(Category.class).orderBy("displayOrder").list();
	}

	public Category getCategoryById(Long id) {
		Category cat = db.fetch(Category.class, id);
		if (cat == null) {
			throw new ApiException(ApiError.ENTITY_NOT_FOUND, "Category", "Category not found.");
		}
		return cat;
	}

	@Transactional
	public Category createCategory(CategoryBean bean) {
		bean.validate(true);
		long maxDisplayOrder = getCategories().stream().mapToLong(c -> c.displayOrder).max().orElseGet(() -> 0);
		Category category = new Category();
		category.name = bean.name;
		category.tag = bean.tag;
		category.description = bean.description;
		category.displayOrder = maxDisplayOrder + 1;
		this.db.insert(category);
		return category;
	}

	@Transactional
	public Category updateCategory(Long id, CategoryBean bean) {
		bean.validate(false);
		Category category = this.getCategoryById(id);
		category.name = bean.name;
		category.tag = bean.tag;
		category.description = bean.description;
		this.db.update(category);
		return category;
	}

	@Transactional
	public void deleteCategory(Long id) {
		Category category = this.getCategoryById(id);
		if (getArticles(category, 1).page.isEmpty) {
			this.db.remove(category);
		} else {
			throw new ApiException(ApiError.OPERATION_FAILED, "category", "Cannot delete non-empty category.");
		}
	}

	@Transactional
	public void sortCategories(List<Long> ids) {
		List<Category> categories = getCategories();
		sortEntities(categories, ids);
	}

	public List<Article> getPublishedArticles(int maxResults) {
		List<Article> articles = this.redisService.get(KEY_RECENT_ARTICLES, TYPE_LIST_ARTICLE);
		if (articles == null) {
			articles = db.from(Article.class).where("publishAt < ?", System.currentTimeMillis()).orderBy("publishAt")
					.desc().orderBy("id").desc().limit(maxResults).list();
			this.redisService.set(KEY_RECENT_ARTICLES, articles, KEY_TIMEOUT);
		}
		return articles;
	}

	public PagedResults<Article> getPublishedArticles(Category category, int pageIndex) {
		long ts = System.currentTimeMillis();
		PagedResults<Article> articles = null;
		if (pageIndex == 1) {
			articles = this.redisService.get(KEY_ARTICLES_FIRST_PAGE + category.id, TYPE_PAGE_RESULTS_ARTICLE);
		}
		if (articles == null) {
			articles = this.db.from(Article.class).where("categoryId = ? AND publishAt < ?", category.id, ts)
					.orderBy("publishAt").desc().orderBy("id").desc().list(pageIndex, ITEMS_PER_PAGE);
			if (pageIndex == 1) {
				this.redisService.set(KEY_ARTICLES_FIRST_PAGE + category.id, articles, KEY_TIMEOUT);
			}
		}
		return articles;
	}

	public PagedResults<Article> getArticles(int pageIndex) {
		return this.db.from(Article.class).orderBy("publishAt").desc().orderBy("id").desc().list(pageIndex,
				ITEMS_PER_PAGE);
	}

	public PagedResults<Article> getArticles(Category category, int pageIndex) {
		return this.db.from(Article.class).where("categoryId = ?", category.id).orderBy("publishAt").desc()
				.orderBy("id").desc().list(pageIndex, ITEMS_PER_PAGE);
	}

	public Article getPublishedById(Long id) {
		Article article = getById(id);
		if (article.publishAt > System.currentTimeMillis()) {
			User user = HttpContext.getCurrentUser();
			if (user == null || user.role.value > Role.CONTRIBUTOR.value) {
				throw new ApiException(ApiError.ENTITY_NOT_FOUND, "Article", "Article not found.");
			}
		}
		return article;
	}

	@Transactional
	public Article createArticle(User user, ArticleBean bean) {
		bean.validate(true);
		getCategoryById(bean.categoryId);
		Article article = new Article();
		article.id = IdUtil.nextId();
		article.userId = user.id;
		article.categoryId = bean.categoryId;
		article.name = bean.name;
		article.description = bean.description;
		article.publishAt = bean.publishAt;
		article.tags = bean.tags;

		AttachmentBean atta = new AttachmentBean();
		atta.name = article.name;
		atta.data = bean.image;
		article.imageId = attachmentService.createAttachment(user, atta).id;

		article.textId = textService.createText(bean.content).id;

		this.db.insert(article);
		return article;
	}

	/**
	 * 导入文章
	 * 
	 * @param user   当前登录用户
	 * @param bean   从页面传递过来的文章值对象，其content做了区分复用（将就用了，不改了）
	 * @param source 源=local（本地导入）|web（网络导入）
	 * @return
	 * @throws Exception
	 */
	@Transactional
	public Article importArticle(User user, ArticleBean bean, String source) throws Exception {
		String hdImage = settingService.getWebsiteFromCache().hdImage;
		boolean usingHD = false;
		if (null != hdImage && hdImage.length() > 0) {
			usingHD = hdImage.toLowerCase().equals("hd")? true: false;
		}
		Article article = new Article();
		List<String> lines;
		String fileDir = "";
		if ("local".equals(source.trim().toLowerCase())) {// 将Markdown文件及其图片文件都上传到服务器了
			File file = new File(bean.content);// bean.content 是借用来存储服务器上Markdown文件的绝对位置的，例如：/Users/kevin/temp/test.md
			fileDir = file.getParent();// 服务器上存放Markdown文件的文件夹，供图片标签的相对路径用
			lines = MarkdownFileUtil.readLines(file);// 读取MD源文件
		} else {// 将Markdown文件内容复制到导入页面的文本块中上传到服务后台，值为web
			lines = MarkdownFileUtil.readLines(bean.content);
		}

		Map<Integer, MarkdownImageBean> imgs = MarkdownFileUtil.readImageLines(lines);// 获取MD源文件中的图片标记

		for (MarkdownImageBean img : imgs.values()) {
			String url = img.getUrl();
			String type = img.getType();
			String tip = img.getTip();
			String location = img.getLocation();
			Attachment attachment = null;
			if ("web".equals(location)) {// 如果是网络图片就导入到系统的附件中
				attachment = attachmentService.importWebAttachment(user, url, type, tip);// 导入附件
			} else {// 处理本地图片，图片标签一般是这样的: ![检查防火墙状态](images/检查防火墙状态.png)
				url = fileDir + System.getProperty("file.separator") + url; // 转换成服务器上的绝对文件路径
				attachment = attachmentService.importLocalAttachment(user, url, tip);// 导入附件
			}
			long attachmentId = attachment.id;
			img.setAttachmentId(attachmentId);
			String articleImage;
			if (usingHD) {
				articleImage = "![" + tip + "](" + "/files/attachments/" + attachmentId + "/0)";//高清图
			} else {
				articleImage = "![" + tip + "](" + "/files/attachments/" + attachmentId + "/l)";
			}
			img.setImageMark(articleImage);// 替换图片标记为iTranswarp附件格式
			if (article.imageId == 0) {// 导入的Article的封面图片使用文章的第一张图片
				article.imageId = attachmentId;
			}
		}
		// 更新原 MD 文件中的图片标记，并将所有的文章内容合并到一个字符串中
		StringBuffer sb = new StringBuffer();
		String separator = System.getProperty("line.separator");
		for (int i = 0; i < lines.size(); i++) {// 替换MD文件内容中的图片标签
			if (imgs.containsKey(i)) {
				lines.set(i, imgs.get(i).getImageMark());
			}
			sb.append(lines.get(i)).append(separator).append(separator);// 合并更新了图片标记后的每一行。按照markdwon的惯用模式，段和段之间有个空行，所以需要换行两次
		}

		article.id = IdUtil.nextId();
		article.userId = user.id;
		article.categoryId = bean.categoryId;
		article.name = bean.name;
		article.description = bean.description;
		article.publishAt = bean.publishAt;
		article.tags = bean.tags;
		article.textId = textService.createText(sb.toString()).id;

		this.db.insert(article);
		return article;
	}

	@Transactional
	public Article deleteArticle(User user, Long id) {
		Article article = this.getById(id);
		checkPermission(user, article.userId);
		this.db.remove(article);
		return article;
	}

	@Transactional
	public Article updateArticle(User user, Long id, ArticleBean bean) {
		bean.validate(false);
		Article article = this.getById(id);
		checkPermission(user, article.userId);
		article.categoryId = bean.categoryId;
		article.name = bean.name;
		article.description = bean.description;
		article.publishAt = bean.publishAt;
		article.tags = bean.tags;
		if (bean.image != null) {
			AttachmentBean atta = new AttachmentBean();
			atta.name = article.name;
			atta.data = bean.image;
			article.imageId = attachmentService.createAttachment(user, atta).id;
		}
		if (bean.content != null) {
			article.textId = textService.createText(bean.content).id;
		}
		this.db.update(article);
		return article;
	}

	static final TypeReference<List<Article>> TYPE_LIST_ARTICLE = new TypeReference<>() {
	};

	static final TypeReference<PagedResults<Article>> TYPE_PAGE_RESULTS_ARTICLE = new TypeReference<>() {
	};
}
