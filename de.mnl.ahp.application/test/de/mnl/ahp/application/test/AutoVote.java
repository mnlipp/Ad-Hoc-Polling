/*
 * Ad Hoc Polling Application
 * Copyright (C) 2018 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.ahp.application.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 */
public class AutoVote {

    public void run(URL url, int code)
            throws IOException, URISyntaxException {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        sleep((long) (Math.random() * 5000));
                        vote(url, code);
                    } catch (IOException | URISyntaxException
                            | InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            };
            thread.start();
            threads.add(thread);
        }
        while (!threads.isEmpty()) {
            try {
                threads.remove(0).join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void vote(URL url, final int code)
            throws IOException, URISyntaxException, ProtocolException {
        HttpClient httpClient = new HttpClient();
        String html = httpClient.get(url);
        assert html.indexOf("body") > 0;
        // Submit code
        html = httpClient.post(url, Map.of(
            "code", "" + code,
            "submit_code", ""));
        // Submit chosen
        int chosen = (int) (Math.random() * 6 + 1);
        html = httpClient.post(url, Map.of("chosen", "" + chosen));
    }

    public class HttpClient {
        CookieManager cookieManager = new CookieManager();

        private HttpURLConnection prepareConnection(URL url)
                throws IOException {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);
            List<HttpCookie> cookies
                = cookieManager.getCookieStore().getCookies();
            if (cookies.size() > 0) {
                conn.setRequestProperty("Cookie", cookies.stream()
                    .map(cookie -> cookie.toString())
                    .collect(Collectors.joining(";")));
            }
            return conn;
        }

        public String get(URL url) throws IOException {
            HttpURLConnection conn = prepareConnection(url);
            conn.setRequestMethod("GET");
            return receiveResponse(url, conn);
        }

        public String post(URL url, Consumer<OutputStream> generator)
                throws IOException {
            HttpURLConnection conn = prepareConnection(url);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.connect();
            generator.accept(conn.getOutputStream());
            return receiveResponse(url, conn);
        }

        public String post(URL url, Map<String, String> params)
                throws IOException {
            return post(url, os -> {
                try (Writer out = new OutputStreamWriter(os)) {
                    boolean first = true;
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        if (first) {
                            first = false;
                        } else {
                            out.write('&');
                        }
                        out.write(URLEncoder.encode(entry.getKey(), "UTF-8"));
                        out.write('=');
                        out.write(URLEncoder.encode(entry.getValue(), "UTF-8"));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        private String receiveResponse(URL url, HttpURLConnection conn)
                throws IOException {
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {
                return get(new URL(url, conn.getHeaderField("Location")));
            }
            try {
                cookieManager.put(url.toURI(), conn.getHeaderFields());
            } catch (URISyntaxException e) {
                // Unlikely.
            }
            try (BufferedReader buffer = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
                String response
                    = buffer.lines().collect(Collectors.joining("\n"));
                conn.disconnect();
                return response;
            }
        }
    }

    public static void main(String[] args) {
//      final URL url = new URL("https://ad-hoc-poll.herokuapp.com/");
//      final URL url = new URL("http://localhost:5001/");
        try {
            URL url = new URL(args[0]);
            new AutoVote().run(url, Integer.parseInt(args[1]));
        } catch (NumberFormatException | IOException
                | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
