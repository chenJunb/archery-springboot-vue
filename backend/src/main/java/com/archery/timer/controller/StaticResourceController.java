package com.archery.timer.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/web")
@Slf4j
public class StaticResourceController {

    @Value("${app.web.resources:}")
    private String webResourcesPath;

    private Path frontendDir;

    @PostConstruct
    public void init() {
        if (webResourcesPath != null && !webResourcesPath.isEmpty()) {
            frontendDir = Paths.get(webResourcesPath);
            log.info("静态资源目录: {}", frontendDir.toAbsolutePath());
        } else {
            // 从环境变量获取路径
            String appInstallDir = System.getenv("APP_INSTALL_DIR");
            if (appInstallDir != null && !appInstallDir.isEmpty()) {
                frontendDir = Paths.get(appInstallDir, "resources", "frontend", "dist");
                log.info("使用安装目录静态资源: {}", frontendDir.toAbsolutePath());
            }
        }
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            log.debug("请求静态文件: {}", filename);

            if (frontendDir == null) {
                log.warn("静态资源目录未配置");
                return ResponseEntity.notFound().build();
            }

            Path file = frontendDir.resolve(filename);
            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("文件不存在或不可读: {}", filename);
                return ResponseEntity.notFound().build();
            }

            // 根据文件扩展名确定Content-Type
            String contentType = determineContentType(filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("URL格式错误: {}", filename, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("提供静态资源失败: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".html")) {
            return "text/html;charset=UTF-8";
        } else if (filename.endsWith(".js")) {
            return "application/javascript";
        } else if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";
        }
    }

    @GetMapping("/")
    public ResponseEntity<Resource> serveIndex() {
        return serveFile("index.html");
    }
}