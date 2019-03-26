package com.itranswarp.web.controller;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

import com.itranswarp.Application;
import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;
import com.itranswarp.markdown.Markdown;
import com.itranswarp.model.Article;
import com.itranswarp.model.Board;
import com.itranswarp.model.Category;
import com.itranswarp.model.LocalAuth;
import com.itranswarp.model.OAuth;
import com.itranswarp.model.Reply;
import com.itranswarp.model.SinglePage;
import com.itranswarp.model.Topic;
import com.itranswarp.model.User;
import com.itranswarp.model.Wiki;
import com.itranswarp.model.WikiPage;
import com.itranswarp.oauth.OAuthAuthentication;
import com.itranswarp.oauth.OAuthProviders;
import com.itranswarp.oauth.provider.AbstractOAuthProvider;
import com.itranswarp.service.ViewService;
import com.itranswarp.util.CookieUtil;
import com.itranswarp.util.HashUtil;
import com.itranswarp.util.HttpUtil;
import com.itranswarp.warpdb.PagedResults;
import com.itranswarp.web.filter.HttpContext;
import com.itranswarp.web.view.i18n.Translators;

/**
 * Mvc controller for all page views.
 * 
 * @author liaoxuefeng
 */
@Controller
public class MvcController extends AbstractController {

	@Value("#{applicationConfiguration.name}")
	String name;

	@Value("#{applicationConfiguration.cdn}")
	String cdn;

	@Value("#{applicationConfiguration.profiles eq 'native'}")
	Boolean dev;

	@Autowired
	Translators translators;

	@Autowired
	LocaleResolver localeResolver;

	@Autowired
	Markdown markdown;

	@Autowired
	OAuthProviders oauthProviders;

	@Autowired
	ViewService viewService;

	///////////////////////////////////////////////////////////////////////////////////////////////
	// index
	///////////////////////////////////////////////////////////////////////////////////////////////
	@GetMapping("/")
	public ModelAndView index() {
		List<Article> recentArticles = this.articleService.getPublishedArticles(10);
		return prepareModelAndView("index.html", Map.of("recentArticles", recentArticles));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// category and article
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/category/" + ID)
	public ModelAndView category(@PathVariable("id") long id,
			@RequestParam(value = "page", defaultValue = "1") int pageIndex) {
		Category category = articleService.getCategoryFromCache(id);
		PagedResults<Article> pr = articleService.getPublishedArticles(category, pageIndex);
		List<Article> articles = pr.getResults();
		if (!articles.isEmpty()) {
			long[] views = this.viewService.getViews(articles.stream().map(a -> a.id).toArray());
			int n = 0;
			for (Article article : articles) {
				article.views += views[n];
				n++;
			}
		}
		return prepareModelAndView("category.html",
				Map.of("category", category, "page", pr.page, "articles", articles));
	}

	@GetMapping("/article/" + ID)
	public ModelAndView article(@PathVariable("id") long id) {
		Article article = articleService.getPublishedById(id);
		article.views += viewService.increaseArticleViews(id);
		Category category = articleService.getCategoryFromCache(article.categoryId);
		User author = userService.getUserFromCache(article.userId);
		String content = textService.getHtmlFromCache(article.textId);
		return prepareModelAndView("article.html",
				Map.of("article", article, "author", author, "category", category, "content", content));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// wiki
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/wiki/" + ID)
	public ModelAndView wiki(@PathVariable("id") long id) {
		Wiki wiki = wikiService.getWikiTreeFromCache(id);
		wiki.views += viewService.increaseWikiViews(id);
		String content = textService.getHtmlFromCache(wiki.textId);
		return prepareModelAndView("wiki.html", Map.of("wiki", wiki, "current", wiki, "content", content));
	}

	@GetMapping("/wiki/" + ID + "/" + ID2)
	public ModelAndView wikiPage(@PathVariable("id") long id, @PathVariable("id2") long pid) {
		Wiki wiki = wikiService.getWikiTreeFromCache(id);
		WikiPage wikiPage = wikiService.getWikiPageById(pid);
		if (wikiPage.wikiId != wiki.id) {
			return notFound();
		}
		wikiPage.views += viewService.increaseWikiPageViews(pid);
		String content = textService.getHtmlFromCache(wikiPage.textId);
		return prepareModelAndView("wiki.html", Map.of("wiki", wiki, "current", wikiPage, "content", content));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// discuss
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/discuss")
	public ModelAndView discuss() {
		List<Board> boards = boardService.getBoards();
		return prepareModelAndView("discuss.html", Map.of("boards", boards));
	}

	@GetMapping("/discuss/" + ID)
	public ModelAndView board(@PathVariable("id") long id,
			@RequestParam(value = "page", defaultValue = "1") int pageIndex) {
		Board board = boardService.getBoardFromCache(id);
		PagedResults<Topic> pr = boardService.getTopics(board, pageIndex);
		return prepareModelAndView("board.html", Map.of("board", board, "page", pr.page, "topics", pr.results));
	}

	@GetMapping("/discuss/" + ID + "/" + ID2)
	public ModelAndView topic(@PathVariable("id") long id, @PathVariable("id2") long tid,
			@RequestParam(value = "page", defaultValue = "1") int pageIndex) {
		Board board = boardService.getBoardFromCache(id);
		Topic topic = boardService.getTopicById(tid);
		if (topic.boardId != board.id) {
			return notFound();
		}
		PagedResults<Reply> pr = boardService.getReplies(topic, pageIndex);
		return prepareModelAndView("topic.html",
				Map.of("board", board, "topic", topic, "page", pr.page, "replies", pr.results));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// single page
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/single/" + ID)
	public ModelAndView singlePage(@PathVariable("id") long id) {
		SinglePage singlePage = singlePageService.getPublishedById(id);
		String content = textService.getHtmlFromCache(singlePage.textId);
		return prepareModelAndView("single.html", Map.of("singlePage", singlePage, "content", content));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// user and profile
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/user/" + ID)
	public ModelAndView user(@PathVariable("id") long id) {
		User user = userService.getById(id);
		return prepareModelAndView("profile.html", Map.of("user", user));
	}

	@GetMapping("/profile")
	public ModelAndView profile() {
		User user = HttpContext.getRequiredCurrentUser();
		return prepareModelAndView("profile.html", Map.of("user", user));
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// sign in
	///////////////////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/auth/signin")
	public ModelAndView signin(@RequestParam(value = "type", defaultValue = "oauth") String type) {
		return prepareModelAndView("signin.html",
				Map.of("type", type, "oauthConfigurations", this.oauthProviders.getOAuthConfigurations()));
	}

	@PostMapping("/auth/signin")
	public ModelAndView localSignIn(@RequestParam("email") String email, @RequestParam("passwd") String password,
			HttpServletRequest request, HttpServletResponse response) {
		if (password.length() != 64) {
			return passwordAuthFailed();
		}
		// try find user by email:
		email = email.strip().toLowerCase();
		User user = userService.fetchUserByEmail(email);
		if (user == null) {
			return passwordAuthFailed();
		}
		// try find local auth by userId:
		LocalAuth auth = userService.fetchLocalAuthByUserId(user.id);
		if (auth == null) {
			return passwordAuthFailed();
		}
		// validate password:
		String expectedPassword = HashUtil.hmacSha256(password, auth.salt);
		if (!expectedPassword.equals(auth.passwd)) {
			return passwordAuthFailed();
		}
		// set cookie:
		String cookieStr = CookieUtil.encodeSessionCookie(auth, System.currentTimeMillis() + LOCAL_EXPIRES_IN_MILLIS,
				encryptService.getSessionHmacKey());
		CookieUtil.setSessionCookie(request, response, cookieStr, LOCAL_EXPIRES_IN_SECONDS);
		return new ModelAndView("redirect:" + HttpUtil.getReferer(request));
	}

	private ModelAndView passwordAuthFailed() {
		return prepareModelAndView("signin.html", Map.of("type", "passauth", "oauthConfigurations",
				this.oauthProviders.getOAuthConfigurations(), "error", Boolean.TRUE));
	}

	@GetMapping("/auth/from/{authProviderId}")
	public String oauthFrom(@PathVariable("authProviderId") String authProviderId, HttpServletRequest request) {
		AbstractOAuthProvider provider = this.oauthProviders.getOAuthProvider(authProviderId);
		String url = HttpUtil.getScheme(request) + "://" + request.getServerName() + "/auth/callback/" + authProviderId;
		return "redirect:" + provider.getAuthenticateUrl(url);
	}

	@GetMapping("/auth/callback/{authProviderId}")
	public String oauthCallback(@PathVariable("authProviderId") String authProviderId,
			@RequestParam("code") String code, HttpServletRequest request, HttpServletResponse response) {
		AbstractOAuthProvider provider = this.oauthProviders.getOAuthProvider(authProviderId);
		String url = HttpUtil.getScheme(request) + "://" + request.getServerName() + "/auth/callback/" + authProviderId;
		OAuthAuthentication authentication = null;
		try {
			authentication = provider.getAuthentication(code, url);
		} catch (Exception e) {
			throw new ApiException(ApiError.AUTH_SIGNIN_FAILED, null, "Signin from OAuth failed.");
		}
		OAuth auth = this.userService.getOAuth(authProviderId, authentication);
		String cookieStr = CookieUtil.encodeSessionCookie(auth, encryptService.getSessionHmacKey());
		CookieUtil.setSessionCookie(request, response, cookieStr, (int) authentication.getExpires().toSeconds());
		return "redirect:" + HttpUtil.getReferer(request);
	}

	@GetMapping("/auth/signout")
	public String signOut(HttpServletRequest request, HttpServletResponse response) {
		CookieUtil.deleteSessionCookie(request, response);
		return "redirect:" + HttpUtil.getReferer(request);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// utility method
	///////////////////////////////////////////////////////////////////////////////////////////////

	ModelAndView prepareModelAndView(String view) {
		ModelAndView mv = new ModelAndView(view);
		appendGlobalModel(mv);
		return mv;
	}

	ModelAndView prepareModelAndView(String view, Map<String, Object> model) {
		ModelAndView mv = new ModelAndView(view, model);
		appendGlobalModel(mv);
		return mv;
	}

	ModelAndView notFound() {
		ModelAndView mv = new ModelAndView("/404.html");
		appendGlobalModel(mv);
		return mv;
	}

	private void appendGlobalModel(ModelAndView mv) {
		@SuppressWarnings("resource")
		HttpContext ctx = HttpContext.getContext();
		mv.addObject("__name__", this.name);
		mv.addObject("__cdn__", this.cdn);
		mv.addObject("__dev__", this.dev);
		mv.addObject("__website__", settingService.getWebsiteFromCache());
		mv.addObject("__user__", ctx.user);
		mv.addObject("__navigations__", navigationService.getNavigationsFromCache());
		mv.addObject("__timestamp__", ctx.timestamp);
		mv.addObject("__translator__", translators.getTranslator(localeResolver.resolveLocale(ctx.request)));
		mv.addObject("__version__", Application.VERSION);
		ctx.response.setHeader("X-Execution-Time", String.valueOf(System.currentTimeMillis() - ctx.timestamp));
	}

	private static final int LOCAL_EXPIRES_IN_SECONDS = 3600 * 24 * 7;
	private static final long LOCAL_EXPIRES_IN_MILLIS = LOCAL_EXPIRES_IN_SECONDS * 1000L;

}