package com.dev.nbl.util.downloading;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SongDownloader {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern NOTE_BLOCK_WORLD = Pattern.compile("^(?:(?:https?://)?(?:www\\.)?noteblock\\.world/song/|nbw:)?([A-Za-z0-9_-]{1,80})/?(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);

    public static CompletableFuture<Path> download(String source, File outputDirectory, String songName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Matcher world = NOTE_BLOCK_WORLD.matcher(source.trim());

                if (!world.matches()) throw new IllegalArgumentException("Expected a Note Block World song URL or ID");

                String name = (songName == null || songName.isBlank()) ? "song" : songName;
                Path target = outputDirectory.toPath().resolve(name + ".nbs").toAbsolutePath().normalize();
                Files.createDirectories(target.getParent());

                downloadNbs(world.group(1), target);

                return target;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Thread::startVirtualThread);
    }

    private static void downloadNbs(String id, Path target) throws IOException, InterruptedException {
        String url;

        try (InputStream response = get("https://api.noteblock.world/v1/song/" + id + "/open", "src", "downloadButton")) {
            url = new String(response.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        try (ZipInputStream zip = new ZipInputStream(get(url))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().equals("song.nbs")) {
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }

        throw new IOException("Downloaded archive does not contain song.nbs");
    }

    private static InputStream get(String url, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2));

        if (headers.length > 0) request.headers(headers);

        HttpResponse<InputStream> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            response.body().close();

            throw new IOException("Download returned HTTP " + response.statusCode());
        }

        return response.body();
    }
}