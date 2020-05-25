[iTranswarp](https://github.com/michaelliao/itranswarp) 是廖雪峰大神官方网站的开源 CMS，用来托管个人的网站，简洁够用。

![4时如梭，4季如歌](/files/attachments/1349284834312256/0)

# 1 技术架构

iTranswarp 主体上是使用了 Spring Boot 2.2.6 的一个单体应用，其页面模板引擎为 pebbletemplates，并且使用了 redis 缓存和全文检索 lucene，数据存储使用其自定义的简化数据持久框架 warpdb，其底层注入 JdbcTemplate 完成数据持久化。

数据库是常用的 MySQL，使用了 HikariCP 数据源连接池。

Markdown 解析器使用的是 commonmark-java：一个基于 [CommonMark](http://commonmark.org/) 规范解析和渲染 Markdown 文本的 Java 库，特点是小、快、灵活。后续需要通过这一块扩展 gitbook 内容直接导入（wiki）的功能。

使用了 JDK 11版本。 

# 2 程序分析

## 2.1 数据持久化

iTranswarp 的数据持久化是通过其自定义的简化数据持久框架 warpdb 来完成的。

在 WarpDb 类里面使用了 Spring 的 JdbcTemplate 来完成最终的数据持久化操作。

```java
public class WarpDb {

	final Log log = LogFactory.getLog(getClass());

	JdbcTemplate jdbcTemplate;
```

warpdb 持久化框架最重要的一个类是范型化的 `Mapper<T>` 类：

```java
final class Mapper<T> {

	final Class<T> entityClass;
	final String tableName;

	// @Id property:
	final AccessibleProperty[] ids;
	// @Version property:
	final AccessibleProperty version;

	// all properties including @Id, key is property name (NOT column name)
	final List<AccessibleProperty> allProperties;

	// lower-case property name -> AccessibleProperty
	final Map<String, AccessibleProperty> allPropertiesMap;

	final List<AccessibleProperty> insertableProperties;
	final List<AccessibleProperty> updatableProperties;

	// lower-case property name -> AccessibleProperty
	final Map<String, AccessibleProperty> updatablePropertiesMap;

	final BeanRowMapper<T> rowMapper;

	final String selectSQL;
	final String insertSQL;
	final String insertIgnoreSQL;
	final String updateSQL;
	final String deleteSQL;
	final String whereIdsEquals;

	final Listener prePersist;
	final Listener preUpdate;
	final Listener preRemove;
	final Listener postLoad;
	final Listener postPersist;
	final Listener postUpdate;
	final Listener postRemove;
  ...
```

可以通过 ArticleService 文章服务类的 createArticle 方法看到清晰的数据操作过程。

```java
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
```

- 创建实体 Article（使用 JPA 注解 @Entity、@Table、@Column 等进行标注），并设置各种属性；
- 调用 WarpDb 的 insert 方法，将实体 Article 存入数据库；

持久化框架通过传入的实体对象获取其类其 `Mapper<Article>`，构建对应的 sql，最终通过 jdbcTemplate 执行这段sql，将其存储到数据库中。

```java
	private <T> boolean doInsert(boolean ignore, T bean) {
		try {
			int rows;
			final Mapper<?> mapper = getMapper(bean.getClass());
			final String sql = ignore ? mapper.insertIgnoreSQL : mapper.insertSQL;
			...
			rows = jdbcTemplate.update(sql, args);
      ...
	}

```

## 2.2 视图模板

iTranswarp 的视图模板使用的是不太常见、但是效率较高的 Pebble Templates：简单高效，容易上手。

Pebble 官方提供了 Spring Boot 的 starter 集成，但是 iTranswarp 使用了原始的集成方式：在 MvcConfiguration 类中注册了 ViewResolver 为 Spring MVC 提供视图解析器。

```java
	@Bean
	public ViewResolver pebbleViewResolver(@Autowired Extension extension) {
		// disable cache for native profile:
		boolean cache = !"native".equals(activeProfile);
		logger.info("set cache as {} for active profile is {}.", cache, activeProfile);
		PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(true).cacheActive(cache).extension(extension)
				.loader(new ClasspathLoader()).build();
		PebbleViewResolver viewResolver = new PebbleViewResolver();
		viewResolver.setPrefix("templates/");
		viewResolver.setSuffix("");
		viewResolver.setPebbleEngine(engine);
		return viewResolver;
	}
```

- 视图解析前缀为 templates/；
- 视图解析后缀为空；

以 ManageController 控制器为例，看看其中的“新建文章” 服务的代码：

```java
	@GetMapping("/article/article_create")
	public ModelAndView articleCreate() {
		return prepareModelAndView("manage/article/article_form.html", Map.of("id", 0, "action", "/api/articles"));
	}
```

- 使用 "templates/manage/article/article_form.html" 这个模板。

模板文件 _base.html 是最基础的页面，可以在其上添加你需要的内容，例如网站备案信息。

> 为了简便起见（毕竟我只用一次），硬编码添加，没有扩展为“管理控制台”里面的设置属性。

```html
	<div id="footer">
		<div class="x-footer uk-container x-container">
			<hr>
			<div class="uk-grid">
				<div class="x-footer-copyright uk-width-small-1-2 uk-width-medium-1-3">
					<p>
						<a href="/" title="version={{ __version__ }}">{{ __website__.name }}</a> {{ __website__.copyright|raw }}
						<a href="http://www.beian.miit.gov.cn/" target="blank">蜀ICP备20013663号</a>
						<br>
						Powered by <a href="https://github.com/michaelliao/itranswarp" target="_blank">iTranswarp</a>
					</p>
				</div>
...
```

## 2.3 数据库表

数据库表命名清晰，自说明强。

系统配套数据库表19张，其作用分别如下：

| 序号 | 表名         | 用途             | 说明                                                         |
| ---- | ------------ | ---------------- | ------------------------------------------------------------ |
| 1    | users        | 用户表           | 需要线下添加用户，线上不需要注册用户                         |
| 2    | local_auths  | 本地用户认证信息 | 存放users中用户的密码                                        |
| 3    | oauths       | 第三方认证       |                                                              |
| 4    | ad_slots     | 广告位           | 在管理控制台中的“广告-广告位”功能中设置                      |
| 5    | ad_periods   | 广告期限         | 在管理控制台中的“广告-广告期限”功能中设置                    |
| 6    | ad_materials | 广告素材         | 需要在有广告位和广告期限的前提下，使用sponsor用户登录，在管理控制台中的“广告-广告素材”功能中设置 |
| 7    | settings     | 设置表           | 在管理控制台中的“设置”功能中配置                             |
| 8    | wikis        | 维基             | 创建书籍、教程用，在管理控制台中的“维基”功能中维护           |
| 9    | wiki_pages   | 维基页面         | 存放维基页面，使用Markdown编辑，内容通过textId存入texts表中，需要扩展成通过gitbook批量导入一本书 |
| 10   | navigations  | 导航             | 在管理控制台中的“导航”功能中配置，在首页的顶部导航栏显示。导航有5种类型：/category/xxx，文章类型（需要先创建文章类型，并在数据库中查询id进行配置导航）；/single/xxx，页面（需要在管理控制台“页面”创建，并在数据库中查询id进行配置导航）；/wiki/xxx，维基，就是教程了，也是先创建在配置导航；/discuss，论坛（系统内置）；外部链接 |
| 11   | boards       | 论坛             | 在管理控制台中的“讨论”功能中维护                             |
| 12   | articles     | 文章             | 在管理控制台中的“文章”功能中维护                             |
| 13   | topics       | 话题             | 文章中第一个评论，评论的答复在replies中。也是论坛的话题。文章评论和论坛话题混到一起，有点儿不清晰 |
| 14   | replies      | 回复             | 文章中评论的回复，话题的回复                                 |
| 15   | attachments  | 附件             | 例如文章中的图片，通过imageId指定到附件中的记录，附件记录中的resourceId，在resources表中以base64编码存储图片 |
| 16   | resources    | 资源             | 资源存储表，比如存储文章、wiki中的图片，字段content需要将类型从mediumtext修改成LongText，支持高清的图片 |
| 17   | single_pages | 页面             | 在管理控制台中的“页面”功能中维护                             |
| 18   | categories   | 文章类型         | 是文章的分类，比如设置“程序人生”文章类别                     |
| 19   | texts        | 文本             | 存放文本，如文章（articles）通过textId将Markdown文本存储在这张表的一条记录中。每做一次修改保存就会在这里添加一条记录。content字段类型text需要修改为mediumtext以容纳更多的文字 |

这样 iTranswarp 就将所有的内容都存放到 MySQL 数据库中了，而不需要使用服务器文件系统，备份 CMS 网站就变成了备份数据库。在小型个人网站应用场景中，数据量不会特别大，这样的设计确实非常方便了。

## 2.4 系统角色 

系统内有5种角色，由 Role 类来定义：

```java
package com.itranswarp.enums;
public enum Role {
	ADMIN(0),
	EDITOR(10),
	CONTRIBUTOR(100),
	SPONSOR(1_000),
	SUBSCRIBER(10_000);
	public final int value;

	Role(int value) {
		this.value = value;
	}
}
```

# 3 使用说明

右上角地图图标是国际设定，支持英语和中文两种语言。

系统的设置、内容创作，都需要在系统内登录。

查看数据库 users 表，登录用户有5个，默认密码为 password：

| 用户名      | 用户邮箱                   | 角色        | 密码     |
| ----------- | -------------------------- | ----------- | -------- |
| admin       | admin@itranswarp.com       | ADMIN       | password |
| editor      | editor@itranswarp.com      | EDITOR      | password |
| contributor | contributor@itranswarp.com | CONTRIBUTOR | password |
| sponsor     | sponsor@itranswarp.com     | SPONSOR     | password |
| subscriber  | subscriber@itranswarp.com  | SUBSCRIBER  | password |

系统的维护操作，都可以使用 admin 用户登录，使用管理控制台进行维护、内容创作。

管理控制台管理的内容，请参考“数据库表”小节中的“说明”列。

# 4 系统扩展

为了更好地维护网站内容，需要提供在线 api：

- 支持导入服务器本地指定目录下的 Markdown 文章（方便离线写作或其他网站备份下来的文章，并要将待导入文章中的图片下载解析并存入本 CMS 系统的数据库表中）；
- 支持导入gitbook到 wiki 中（图片支持本地图片和网络图片的导入）；
- 支持导出 wiki 到 gitbook 格式到服务器的某个目录并 zip 下载；
- 支持导出某一篇文章或某一些文章到服务器的某个目录并 zip 下载。

## 4.1 扩展工具类

对 iTranswarp 的工具类进行扩展或新增，以统一提供额外的功能。

### 4.1.1 图像处理类 ImageUtil

主要添加读取图像到字符数组的方法，供文章或wiki中的附件（图片）导入调用。

#### 4.1.1.1 readWebImageStream

对 ImageUtil 类进行扩展，添加 readWebImageStream 方法，使用apache HttpClients 组件，从网络上读取图片文件。

```java
/**
	 * 从网络链接获取图片
	 * @param imgUrl 网络图片地址Ø
	 * @param imgType 图片类型：jpeg｜bmp｜gif｜png
	 * @return 存放图片的字节数组
	 * @throws Exception
	 */
public static byte[] readWebImageStream(String imgUrl, String imgType) throws Exception {
  byte[] bitImg;

  CloseableHttpClient httpClient = HttpClients.createDefault();
  CloseableHttpResponse response = null;
  HttpGet httpGet = new HttpGet(imgUrl);
  // 浏览器表示
  httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.7.6)");
  // 传输的类型
  httpGet.addHeader("Content-Type", "image/" + imgType.toLowerCase());//有效类型为：image/jpeg image/bmp image/gif image/png
  try {
    // 执行请求
    response = httpClient.execute(httpGet);
    // 获得响应的实体对象
    HttpEntity entity = response.getEntity();
    // 包装成高效流
    BufferedInputStream bis = new BufferedInputStream(entity.getContent());
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] byt = new byte[1024 * 8];
    Integer len = -1;
    while ((len = bis.read(byt)) != -1) {
      bos.write(byt, 0, len);
    }
    bitImg = bos.toByteArray();

    bos.close();
    bis.close();
  } finally {
    // 释放连接
    if (null != response) {
      try {
        response.close();
        httpClient.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  return bitImg;
}
```

#### 4.1.1.2 readLocalImageStream

从本地文件路径下读取文件，返回到字节数组中。

```java
/**
	 * 从本地文件中读取到字节数组
	 * @param fileName 文件路径
	 * @return
	 * @throws Exception
	 */
public static byte[] readLocalImageStream(String fileName) throws Exception {
  byte[] bitImg;
  try (InputStream in = new FileInputStream(fileName)) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024 * 8];
    int n = 0;
    while ((n = in.read(buffer)) != -1) {
      out.write(buffer, 0, n);
    }
    bitImg = out.toByteArray();
  }
  return bitImg;
}
```

### 4.1.2 Markdown 文件处理类 MdFileUtil 

这个类主要处理 Markdown 文件的操作。

#### 4.1.2.1 readLines(File file)

将文本文件（一般就是一个 Markdown 文件）按行读取文件到 List 中，后续需要提取其中的图片行，以供从网络上下载图片文件或读取本地图片文件，并以附件的形式导入到 iTranswarp 数据库中。

```java
/**
	 * 按行读取文件到List中
	 * 
	 * @param file 待读取的文件
	 * @return 按行存储的list
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
public static List<String> readLines(File file) throws FileNotFoundException, IOException {
  try (BufferedReader bf = new BufferedReader(new FileReader(file))) {
    List<String> lines = new ArrayList<String>();
    String line;
    // 按行读取字符串
    while ((line = bf.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }
}
```

#### 4.1.2.2 readLines(String content)

从文本 content（一般就是从网页维护传送到后台的一篇 Markdown 文本内容）读取内容到一个 List 中，后续需要提取其中的图片行，以供从网络上下载图片文件或读取本地图片文件，并以附件的形式导入到 iTranswarp 数据库中。

```java
/**
	 * 从文本中按行划分为List
	 * 
	 * @param content
	 * @return
	 */
public static List<String> readLines(String content) {
  String[] strs = content.split(System.getProperty("line.separator"));
  List<String> lines = Arrays.asList(strs);
  return lines;
}
```

#### 4.1.2.3 readImageLines

从给定的 List（是按行存放的 Markdown 文本内容）中读取 image 标签的行到返回的 List 中，后续将从这个返回的图片 List 中获取 image 标签信息供从网络上下载图片文件或读取本地图片文件，并以附件的形式导入到 iTranswarp 数据库中。

```java
/**
	 * 获取图片标记行
	 * 
	 * @param lines MD文件的所有行
	 * @return MD文件中的图片标记行
	 */
public static Map<Integer, MdImageMarkBean> readImageLines(List<String> lines) {
  Map<Integer, MdImageMarkBean> imgs = new HashMap<Integer, MdImageMarkBean>();
  for (int i = 0; i < lines.size(); i++) {
    String line = trimLeft(lines.get(i));
    if (line.startsWith("![") && line.endsWith(")")) {// MarkDown 图片标签为 ![这是一张图片](http://www.abc.com/def.png)
      String imgUrl = line.substring(line.indexOf("](") + 2, line.lastIndexOf(")"));// 提取图片url地址
      String imgTip = line.substring(line.indexOf("![") + 2, line.indexOf("]"));// 提取图片的描述信息
      String imgType = "jpeg";// 提取图片的类型
      if (line.indexOf(".png") != -1) {
        imgType = "png";
      } else if (line.indexOf(".jpg") != -1) {
        imgType = "jpeg";
      } else if (line.indexOf(".gif") != -1) {
        imgType = "gif";
      } else if (line.indexOf(".bpm") != -1) {
        imgType = "bmp";
      }
      // 判断图片是否是web图片
      String location = "local"; // 默认本地图片
      if (line.indexOf("http://") != -1 || line.indexOf("https://") != -1) {
        location = "web";
      }
      String imageMark = "![" + imgTip + "](" + "images/" + imgTip + "." + imgType + ")";// 保存到当前文件的imges目录下
      MdImageMarkBean imgBean = new MdImageMarkBean();// 创建这张图片的Map存储对象
      imgBean.setLine(i);
      imgBean.setUrl(imgUrl);
      imgBean.setTip(imgTip);
      imgBean.setType(imgType);
      imgBean.setLocation(location);// 图片位置：本地local｜网络web
      imgBean.setImageMark(imageMark);// 更新过的图片标签

      imgs.put(i, imgBean);
    }
  }
  return imgs;
}
```

#### 4.1.2.4 writeFile(List\<String\> lines, File file)

将按行存储的 Markdown 内容（lines参数，这个时候，image 标签已经做了对应的替换，如从网络图片更换为本地文件）保存到文件中。

```java
/**
	 * 将按行存储的list写入文件
	 * 
	 * @param lines
	 * @param file
	 * @throws IOException
	 */
public static void writeFile(List<String> lines, File file) throws IOException {
  try (FileWriter fw = new FileWriter(file)) {
    for (int i = 0; i < lines.size(); i++) {
      fw.write(lines.get(i));
      fw.write(System.getProperty("line.separator"));
    }
  }
}
```

#### 4.1.2.5 writeFile(**byte**[] bytes, File file)

将字节数组（一般是从网络流中读取的网络图片）存储到本地文件，例如将简书文章中的网络图片下载保存到本地文件夹中。

```java
/**
	 * 将字节数组写入文件
	 * 
	 * @param bytes
	 * @param file
	 * @throws IOException
	 */
public static void writeFile(byte[] bytes, File file) throws IOException {
  try (FileOutputStream fos = new FileOutputStream(file);
       BufferedOutputStream bos = new BufferedOutputStream(fos)) {
    bos.write(bytes);
  }
}
```

## 4.2 扩展附件服务 AttachmentService 类

扩展 AttachmentService 类，添加2个方法向系统添加附件（也就导入MD文件中的图片）。

### 4.2.1 importWebAttachment

通过 url 导入 web 上的图片到附件中。

```java
/**
	 * 通过url导入网络图片创建附件
	 * @param user 当前登录用户
	 * @param url 图片的网络地址
	 * @param type 图片类型
	 * @param name 存入系统附件的名称
	 * @return
	 * @throws Exception
	 */
@Transactional
public Attachment importWebAttachment(User user, String url, String type, String name) throws Exception {
  AttachmentBean bean = new AttachmentBean();
  byte[] img = ImageUtil.readWebImageStream(url, type);
  bean.data = Base64.getEncoder().encodeToString(img);
  bean.name = name;
  return createAttachment(user, bean);
}
```

### 4.2.2 importLocalAttachment

通过文件路径，导入服务器本地指定文件路径下的图片到附件中。

```java
/**
	 * 导入本地文件（图片）创建附件
	 * @param user 当前登录用户
	 * @param url 服务器上文件绝对路径
	 * @param type 图片类型
	 * @param name 存入系统附件的名称
	 * @return
	 * @throws Exception
	 */
@Transactional
public Attachment importLocalAttachment(User user, String url, String name) throws Exception {
  AttachmentBean bean = new AttachmentBean();
  byte[] img = ImageUtil.readLocalImageStream(url);
  bean.data = Base64.getEncoder().encodeToString(img);
  bean.name = name;
  return createAttachment(user, bean);
}
```

## 4.3 导入文章

导入文章会根据文章的位置，分为本地文章和网络文章：

- 本地文章：Markdown文件及其以来的本地图片文件事先已经写好并上传到服务器的指定目录中；
- 网络文章：在网络上（CSDN或简书等）写好的文章，特点是图片都是上传在网络服务器上；

### 4.3.1 扩展文章服务 ArticleService 类

为其添加导入文章的服务方法 importArticle，在方法内部区分网络文章或本地文章的导入。

```java
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
  for (int i = 0; i < lines.size(); i++) {// 替换MD文件内容中的图片标签
    if (imgs.containsKey(i)) {
      lines.set(i, imgs.get(i).getImageMark());
    }
    sb.append(lines.get(i)).append(System.getProperty("line.separator"));// 合并更新了图片标记后的每一行
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
```

### 4.3.2 扩展 ApiController

为其添加方法 articleImportSource 和 articleImportLocal，供前端 ManageController 调用服务用。

```java
/**
	 * 从网络Markdown源文件导入
	 * @param bean
	 * @return
	 */
@PostMapping("/articles/import/source")
@RoleWith(Role.CONTRIBUTOR)
public Article articleImportSource(@RequestBody ArticleBean bean) {
  Article article = null;
  try {
    article = this.articleService.importArticle(HttpContext.getRequiredCurrentUser(), bean, "web");
  } catch (Exception e) {
    e.printStackTrace();
  }
  if (article != null) {
    this.articleService.deleteArticlesFromCache(article.categoryId);
  }

  return article;
}

/**
	 * 从服务器本地 Markdown 文件导入，需要事先将 Markdown 文件scp到服务器上，用来维护现有离线文章的
	 * @param bean
	 * @return
	 */
@PostMapping("/articles/import/local")
@RoleWith(Role.CONTRIBUTOR)
public Article articleImportLocal(@RequestBody ArticleBean bean) {
  //bean.content 是借用来存储服务器上Markdown文件的绝对位置的，例如：/Users/kevin/temp/test.md
  Article article = null;
  try {
    article = this.articleService.importArticle(HttpContext.getRequiredCurrentUser(), bean, "local");
  } catch (Exception e) {
    e.printStackTrace();
  }
  if (article != null) {
    this.articleService.deleteArticlesFromCache(article.categoryId);
  }

  return article;
}
```

### 4.3.3 扩展 ManageController

提供两个方法 articleImportSource 和 articleImportLocal，连接前端页面和后端服务。

注意其中的 "action" 就是传递到页面供导入文章用的 rest 服务地址。

```java
/**
	 * 从网络 Markdown 源文件的导入，特点是文章中的图片存储在网络上
	 * @return
	 */
@GetMapping("/article/article_import_source")
public ModelAndView articleImportSource() {
  return prepareModelAndView("manage/article/article_import_source_form.html", Map.of("id", 0, "action", "/api/articles/import/source"));
}

/**
	 * 从服务器本地 Markdown 文件导入，特点是文章中的图片在本地
	 * @return
	 */
@GetMapping("/article/article_import_local")
public ModelAndView articleImportLocal() {
  return prepareModelAndView("manage/article/article_import_local_form.html", Map.of("id", 0, "action", "/api/articles/import/local"));
}
```

### 4.3.4 扩展页面

在文章列表页面，添加两个导入按钮，通过其 url 将其连接到两个导入页面：article_import_local_form.html 和 article_import_source_form.html。

```html
<div class="uk-margin">
  <a href="javascript:refresh()" class="uk-button"><i class="uk-icon-refresh"></i> {{ _('Refresh') }}</a>
  <a href="article_create" class="uk-button uk-button-primary uk-float-right"><i class="uk-icon-plus"></i>{{ _('New Article') }}</a>&nbsp;&nbsp;
  <a href="article_import_source" class="uk-button uk-button-primary uk-float-right"><i class="uk-icon-plus"></i>{{ _('Import Article') }}</a>
  <a href="article_import_local" class="uk-button uk-button-primary uk-float-right"><i class="uk-icon-plus"></i>{{ _('Import Local Article') }}</a>
</div>
```

新建的两个导入页面，拷贝自 article_form.html 并略做修改。

## 4.4 导入 wiki

一般而言，大型创作，比如一整套教程、一整本书，使用客户端本地创作还是相对更方便，比如使用 gitbook 管理书籍，使用 Typora 以 Markdown 格式书写内容，图片等处理都非常顺手。

所以就诞生了将 gitbook 写好的一整本书导入到 wiki 中来的需求：

1. 离线写好 gitbook；
2. 在系统内创建 wiki；
3. 将 gitbook 的所有文件上传到服务器某个目录下；
4. 系统提供界面，填写 gitbook 所在的文件路径，然后导入。

### 4.4.1 GitbookSummaryUtil 目录工具类

gitbook 使用 `SUMMARY.md` 文件来管理书籍目录，所以对 gitbook 的导入，主要就是处理这个目录文件内容。

首先创建目录行值对象类 `GitbookSummaryBean` ，存储目录行，并记录父目录信息。

```java
public class GitbookSummaryBean {
	private String content;// 内容：“8个空格* [1.2.1 在路上](第01章 万事开头难/1.2.1onTheWay.md)”，3级内容
	private String title;// 显示用的标题：“1.2.1 在路上”
	private String markdownFile;// 文件地址：“第01章 万事开头难/1.2.1onTheWay.md”
	private int level;// 当前页面所处的级别：3级
	private int displayOrder;// 同层页面显示序号：0
	private long id;// 当前页面的编码，导入wiki page后，就是数据库内的编码
	private GitbookSummaryBean parent;// 当前这个目录文件的父文件，就是挂靠目录树用的


```

按顺序读取 `SUMMARY.md` 文件，将其存入 List 中，重点是同层序号 displayOrder 和目录的父目录的设定。

```java
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
```

### 4.4.2 扩展 WikiService 类

在 wiki 服务类中增加 `importWiki` 方法：创建带父子关系的 wiki page，并将 Markdown 文件内容导入page 中，其中的图片，导入系统附件。

```java
/**
	 * @throws Exception 
	 * 从gitbook导入wiki
	 * @param user
	 * @param bean
	 * @return
	 * @throws IOException 
	 * @throws  
	 */
@Transactional
public Wiki importWiki(User user, WikiImportBean bean) throws Exception {
  String hdImage = settingService.getWebsiteFromCache().hdImage;
  boolean usingHD = false;
  if (null != hdImage && hdImage.length() > 0) {
    usingHD = hdImage.toLowerCase().equals("hd")? true: false;
  }
  long wikiId = bean.wikiId;
  Wiki wiki = this.getById(wikiId);
  String fileName = bean.gitbookPath + System.getProperty("file.separator") + "SUMMARY.md";
  List<GitbookSummaryBean> list = GitbookSummaryUtil.readLines(new File(fileName));
  for (GitbookSummaryBean summary: list) {
    long parentId;
    if (summary.getParent() == null) { // 没有父节点的是“第1章”这样的，直接挂到wiki下
      parentId = wikiId;
    } else {
      parentId = summary.getParent().getId();
    }
    //处理页面文件中的附件（图片）
    String pageFile = bean.gitbookPath + System.getProperty("file.separator") + summary.getMarkdownFile();
    List<String> lines = MarkdownFileUtil.readLines(new File(pageFile));//页面内容
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
        url = pageFile.subSequence(0, pageFile.lastIndexOf("/") + 1) + url; // 转换成服务器上的绝对文件路径
        attachment = attachmentService.importLocalAttachment(user, url, tip);// 导入附件
      }
      long attachmentId = attachment.id;
      img.setAttachmentId(attachmentId);
      String imageMark;
      if (usingHD) {
        imageMark = "![" + tip + "](" + "/files/attachments/" + attachmentId + "/0)";//高清图
      } else {
        imageMark = "![" + tip + "](" + "/files/attachments/" + attachmentId + "/l)";
      }
      img.setImageMark(imageMark);// 替换图片标记为iTranswarp附件格式
    }
    // 更新页面文件中的图片标记，并将所有的页面内容合并到一个字符串中
    StringBuffer sbPage = new StringBuffer();
    for (int i = 0; i < lines.size(); i++) {// 替换MD文件内容中的图片标签
      if (imgs.containsKey(i)) {
        lines.set(i, imgs.get(i).getImageMark());
      }
      sbPage.append(lines.get(i)).append(System.getProperty("line.separator"));// 合并更新了图片标记后的每一行
    }

    WikiPage page = new WikiPage();
    page.wikiId = wikiId;
    page.parentId = parentId;
    page.name = summary.getTitle();
    page.publishAt = wiki.publishAt;//使用wiki的发布时间，一家人就是要整整齐齐嘛
    page.textId = textService.createText(sbPage.toString()).id;
    page.displayOrder = summary.getDisplayOrder();
    this.db.insert(page);

    summary.setId(page.id);//供后续获取父页面id用
  }
  return wiki;
}
```

### 4.4.3 扩展 ApiController 类

首先创建 WikiImportBean 值对象，用来存储从页面上传递回控制器的信息。

```java
public class WikiImportBean extends AbstractRequestBean {
	public String gitbookPath;
	public long wikiId;
	public long publishAt;
	public String content = "New wiki page content";//默认wiki页面的内容
	
	@Override
	public void validate(boolean createMode) {
	}
}

```

在 ApiController 类中添加 wikiImport 方法，将 gitbook 导入系统。

```java
@PostMapping("/wikiImport")
@RoleWith(Role.EDITOR)
public Wiki wikiImport(@RequestBody WikiImportBean bean) {
  Wiki wiki = wikiService.getById(bean.wikiId);
  try {
    wiki = this.wikiService.importWiki(HttpContext.getRequiredCurrentUser(), bean);
  } catch (Exception e) {
    e.printStackTrace();
  }
  this.wikiService.removeWikiFromCache(wiki.id);
  return wiki;
}
```

### 4.4.3 修改 wiki_list.html 页面

在 wiki 行的操作列添加一个按钮，接收 gitbook 在服务器上的文件路径，然后调用后台方法，导入 wiki：

```javascript
importBook: function (w) {// 从gitbook导入wiki
  var now = Date.now();
  UIkit.modal.prompt("{{ _('Gitbook在服务器上的位置') }}:", "{{ _('/Users/kevin/temp/demobook') }}", function (path) {
    postJSON('/api/wikiImport', {
      wikiId: w.id,
      gitbookPath: path,
      publishAt: now,
      content: 'New wiki page content'
    }, function(err, result) {
      if (err) {
        showError(err);
        return;
      }
    });
  });
}
```

## 4.5 用户维护

个人网站，为了避免内容审核维护工作，初期不提供用户评论功能。

所以，需要增加本地用户维护功能：新增用户和用户密码修改（仅限管理员增加，不支持陌生人自行注册）。

用户通过页面登录时，传递到后端的密码不是明文，而是加密处理过的值，如下：

```html
<script src="/static/js/3rdparty/sha256.js"></script>
$('#hashPasswd').val(sha256.hmac(email, pwd));
```

新增 user_form.html 文件，提供用户注册功能，配套提供后台服务代码。

修改 user_list.html 文件，提供用户密码修改功能，同样修改后台服务代码。

## 4.6 图片显示

iTranswarp 中的 文章和wiki，在显示图片时，为了节约网络流量，对图片做了处理，导致图片显示不清晰。

FileController 这个类的 process 方法通过 `attachmentService.downloadAttachment(id, size);` 获取不同大小的图片，并输出到HttpServletResponse 中，供前端浏览器显示用。

```java
@GetMapping("/files/attachments/" + ID)
public void process(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
  process(id, '0', response);
}

@GetMapping("/files/attachments/" + ID + "/0")
public void process0(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
  process(id, '0', response);
}

@GetMapping("/files/attachments/" + ID + "/l")
public void processL(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
  process(id, 'l', response);
}

@GetMapping("/files/attachments/" + ID + "/m")
public void processM(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
  process(id, 'm', response);
}

@GetMapping("/files/attachments/" + ID + "/s")
public void processS(@PathVariable("id") long id, HttpServletResponse response) throws IOException {
  process(id, 's', response);
}

void process(long id, char size, HttpServletResponse response) throws IOException {
  DownloadBean bean = attachmentService.downloadAttachment(id, size);
  response.setContentType(bean.mime);
  response.setContentLength(bean.data.length);
  response.setHeader("Cache-Control", maxAge);
  @SuppressWarnings("resource")
  ServletOutputStream output = response.getOutputStream();
  output.write(bean.data);
  output.flush();
}
```

可以在导入文章或 wiki 时，根据扩展后的 Website.hdImage 属性设置显示文章的大小，高清就使用这样的图片链接：`![Spring 1分层架构](/files/attachments/1349646821622176/0)`

> 系统上新建文章或 wiki（页面）时，暂不处理。
>
> 可以在新建/修改文章或 wiki 页面的 Markdown 文本中将图片标记的最后一个字符修改为 0 来显示高清图片。

## 4.7 小结

导入文章和导入 wiki 这两个功能使用频率不高，所以页面及后台代码并没有做太多的设计，够用就好。

经测试，一本 500+ 页的 gitbook 导入，在我的开发笔记本上导入时间小于30秒。

修改的版本在[这里](https://github.com/gyzhang/itranswarp)，欢迎fork，欢迎star。

Kevin，2020年5月20日，成都。