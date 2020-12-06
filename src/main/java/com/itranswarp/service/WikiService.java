package com.itranswarp.service;

import com.itranswarp.bean.*;
import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;
import com.itranswarp.model.Attachment;
import com.itranswarp.model.User;
import com.itranswarp.model.Wiki;
import com.itranswarp.model.WikiPage;
import com.itranswarp.util.GitbookSummaryUtil;
import com.itranswarp.util.IdUtil;
import com.itranswarp.util.MarkdownFileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WikiService extends AbstractService<Wiki> {

    @Autowired
    TextService textService;

    @Autowired
    AttachmentService attachmentService;

    @Autowired
    SettingService settingService;

    static final String KEY_WIKIS = "__wikis__";

    public void removeWikiFromCache(Long id) {
        this.redisService.del(KEY_WIKIS + id);
    }

    public Wiki getWikiTreeFromCache(Long id) {
        Wiki wiki = this.redisService.get(KEY_WIKIS + id, Wiki.class);
        if (wiki == null) {
            wiki = getWikiTree(id);
            this.redisService.set(KEY_WIKIS + id, wiki);
        }
        return wiki;
    }

    public Wiki getWikiTree(Long id) {
        Wiki wiki = getById(id);
        List<WikiPage> children = getWikiPages(id);
        Map<Long, WikiPage> nodes = new LinkedHashMap<>();
        children.forEach(wp -> {
            nodes.put(wp.id, wp);
        });
        treeIterate(wiki, nodes);
        if (!nodes.isEmpty()) {
            // there is error for tree structure, append to root for fix:
            nodes.forEach((nodeId, node) -> {
                wiki.addChild(node);
            });
        }
        return wiki;
    }

    private List<WikiPage> getWikiPages(Long wikiId) {
        return this.db.from(WikiPage.class).where("wikiId = ?", wikiId).orderBy("parentId").orderBy("displayOrder")
                .orderBy("id").list();
    }

    void treeIterate(Wiki root, Map<Long, WikiPage> nodes) {
        List<Long> toBeRemovedIds = new ArrayList<>();
        for (Long id : nodes.keySet()) {
            WikiPage node = nodes.get(id);
            if (node.parentId == root.id) {
                root.addChild(node);
                toBeRemovedIds.add(id);
            }
        }
        toBeRemovedIds.forEach(id -> {
            nodes.remove(id);
        });
        root.getChildren().forEach(child -> {
            treeIterate(child, nodes);
        });
    }

    void treeIterate(WikiPage root, Map<Long, WikiPage> nodes) {
        List<Long> toBeRemovedIds = new ArrayList<>();
        for (Long id : nodes.keySet()) {
            WikiPage node = nodes.get(id);
            if (node.parentId == root.id) {
                root.addChild(node);
                toBeRemovedIds.add(id);
            }
        }
        toBeRemovedIds.forEach(id -> {
            nodes.remove(id);
        });
        root.getChildren().forEach(child -> {
            treeIterate(child, nodes);
        });
    }

    public List<Wiki> getWikis() {
        return db.from(Wiki.class).orderBy("name").list();
    }

    @Transactional
    public Wiki createWiki(User user, WikiBean bean) {
        bean.validate(true);
        Wiki wiki = new Wiki();
        wiki.id = IdUtil.nextId();
        wiki.name = bean.name;
        wiki.tag = bean.tag;
        wiki.description = bean.description;
        wiki.publishAt = bean.publishAt;
        wiki.textId = this.textService.createText(bean.content).id;

        AttachmentBean atta = new AttachmentBean();
        atta.name = wiki.name;
        atta.data = bean.image;
        wiki.imageId = this.attachmentService.createAttachment(user, atta).id;
        this.db.insert(wiki);
        return wiki;
    }

    /**
     * @param user
     * @param bean
     * @return
     * @throws Exception   从gitbook导入wiki
     * @throws
     */
    @Transactional
    public Wiki importWiki(User user, WikiImportBean bean) throws Exception {
        String hdImage = settingService.getWebsiteFromCache().hdImage;
        boolean usingHD = false;
        String separator = System.getProperty("line.separator");
        if (null != hdImage && hdImage.length() > 0) {
            usingHD = hdImage.toLowerCase().equals("hd") ? true : false;
        }
        long wikiId = bean.wikiId;
        Wiki wiki = this.getById(wikiId);
        String fileName = bean.gitbookPath + System.getProperty("file.separator") + "SUMMARY.md";
        List<GitbookSummaryBean> list = GitbookSummaryUtil.readLines(new File(fileName));
        for (GitbookSummaryBean summary : list) {
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
                    String mdFilePath = pageFile.substring(0, (pageFile.lastIndexOf("/") > 0 ? pageFile.lastIndexOf("/") : pageFile.lastIndexOf(System.getProperty("file.separator"))) + 1);//兼容Windows的\分隔符
                    url = mdFilePath + url; // 转换成服务器上的绝对文件路径
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
                sbPage.append(lines.get(i)).append(separator);// 合并更新了图片标记后的每一行。
            }
            //查找现有page，如果有，则更新
            WikiPage page = null;
            page = this.db.from(WikiPage.class).where("wikiId = ?", wikiId).and("parentId = ?", parentId).and("name = ?", summary.getTitle()).first();
            if (page != null) {//已有page，更新
                page.textId = textService.createText(sbPage.toString()).id;//更新页面内容
                this.db.update(page);
            } else {//没有就新建
                page = new WikiPage();
                page.wikiId = wikiId;
                page.parentId = parentId;
                page.name = summary.getTitle();
                page.publishAt = wiki.publishAt;//使用wiki的发布时间，一家人就是要整整齐齐嘛
                page.textId = textService.createText(sbPage.toString()).id;
                page.displayOrder = summary.getDisplayOrder();
                this.db.insert(page);
            }

            summary.setId(page.id);//供后续获取父页面id用
        }
        return wiki;
    }

    @Transactional
    public Wiki updateWiki(User user, Long id, WikiBean bean) {
        bean.validate(false);
        Wiki wiki = this.getById(id);
        wiki.name = bean.name;
        wiki.tag = bean.tag;
        wiki.description = bean.description;
        wiki.publishAt = bean.publishAt;
        if (bean.content != null) {
            wiki.textId = this.textService.createText(bean.content).id;
        }
        if (bean.image != null) {
            AttachmentBean atta = new AttachmentBean();
            atta.name = wiki.name;
            atta.data = bean.image;
            wiki.imageId = this.attachmentService.createAttachment(user, atta).id;
        }
        this.db.update(wiki);
        return wiki;
    }

    @Transactional
    public WikiPage updateWikiPage(User user, Long id, WikiPageBean bean) {
        bean.validate(false);
        WikiPage wikipage = getWikiPageById(id);
        Wiki wiki = getById(wikipage.wikiId);
        super.checkPermission(user, wiki.userId);
        wikipage.name = bean.name;
        wikipage.publishAt = bean.publishAt;
        if (bean.content != null) {
            wikipage.textId = this.textService.createText(bean.content).id;
        }
        this.db.update(wikipage);
        return wikipage;
    }

    @Transactional
    public WikiPage createWikiPage(User user, Wiki wiki, WikiPageBean bean) {
        bean.validate(true);
        super.checkPermission(user, wiki.userId);
        WikiPage parent = null;
        long parentId = bean.parentId;
        if (wiki.id == parentId) {
            // append as top level:
        } else {
            parent = getWikiPageById(parentId);
            if (parent.wikiId != wiki.id) {
                throw new IllegalArgumentException("Inconsist wikiId for node: " + parent);
            }
        }
        WikiPage lastChild = this.db.from(WikiPage.class).where("wikiId = ? AND parentId = ?", wiki.id, parentId)
                .orderBy("displayOrder").desc().first();
        WikiPage newChild = new WikiPage();
        newChild.wikiId = wiki.id;
        newChild.parentId = parentId;
        newChild.name = bean.name;
        newChild.publishAt = bean.publishAt;
        newChild.textId = textService.createText(bean.content).id;
        newChild.displayOrder = lastChild == null ? 0 : lastChild.displayOrder + 1;
        this.db.insert(newChild);
        return newChild;
    }

    @Transactional
    public WikiPage moveWikiPage(User user, long wikiPageId, long toParentId, int displayIndex) {
        WikiPage wikiPage = getWikiPageById(wikiPageId);
        Wiki wiki = getById(wikiPage.wikiId);
        super.checkPermission(user, wiki.userId);
        if (wikiPage.parentId != toParentId) {
            // check to prevent recursive:
            List<Long> leafToRootIdList = getLeafToRootIds(wiki, toParentId);
            if (leafToRootIdList.contains(wikiPage.id)) {
                throw new ApiException(ApiError.OPERATION_FAILED, null, "Will cause recursive.");
            }
        }
        // update parentId:
        wikiPage.parentId = toParentId;
        this.db.updateProperties(wikiPage, "parentId");
        // sort:
        List<WikiPage> pages = this.db.from(WikiPage.class).where("parentId = ?", toParentId).orderBy("displayOrder")
                .list();
        List<Long> pageIds = new ArrayList<>(pages.stream().map(p -> p.id).collect(Collectors.toList()));
        pageIds.remove(wikiPageId);
        if (displayIndex < 0 || displayIndex > pageIds.size()) {
            pageIds.add(wikiPageId);
        } else {
            pageIds.add(displayIndex, wikiPageId);
        }
        sortEntities(pages, pageIds);
        return wikiPage;
    }

    @Transactional
    public void deleteWiki(User user, Long id) {
        Wiki wiki = getById(id);
        super.checkPermission(user, wiki.userId);
        WikiPage child = this.db.from(WikiPage.class).where("parentId = ?", id).first();
        if (child != null) {
            throw new ApiException(ApiError.OPERATION_FAILED, null, "Could not remove non-empty wiki.");
        }
        this.db.remove(wiki);
        this.attachmentService.deleteAttachment(user, wiki.imageId);
    }

    @Transactional
    public WikiPage deleteWikiPage(User user, Long id) {
        WikiPage wikiPage = getWikiPageById(id);
        Wiki wiki = getById(wikiPage.wikiId);
        super.checkPermission(user, wiki.userId);
        WikiPage child = this.db.from(WikiPage.class).where("parentId = ?", id).first();
        if (child != null) {
            throw new ApiException(ApiError.OPERATION_FAILED, null, "Could not remove non-empty wiki page.");
        }
        this.db.remove(wikiPage);
        return wikiPage;
    }

    public WikiPage getWikiPageById(Long id) {
        WikiPage wikipage = this.db.fetch(WikiPage.class, id);
        if (wikipage == null) {
            throw new ApiException(ApiError.PARAMETER_INVALID, null, "not found");
        }
        return wikipage;
    }

    List<Long> getLeafToRootIds(Wiki wiki, Long leafId) {
        if (leafId.equals(wiki.id)) {
            return List.of(leafId);
        }
        List<Long> nodeIds = new ArrayList<>();
        Long currentId = leafId;
        for (int i = 0; i < 100; i++) {
            nodeIds.add(currentId);
            WikiPage node = getWikiPageById(currentId);
            if (node.parentId == wiki.id) {
                nodeIds.add(wiki.id);
                break;
            }
            currentId = node.parentId;
        }
        return nodeIds;
    }
}
