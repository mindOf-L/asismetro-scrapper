package xyz.asismetro.scrapper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainCollab {

    public static void main(String[] args) throws IOException, InterruptedException {

        ArrayList<Brother> brothers = new ArrayList<>();

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36";
        String contentType = "application/x-www-form-urlencoded";

        // get (home)
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://asismetro.org/"))
                .setHeader("User-Agent", userAgent)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        //System.out.println(response.statusCode());
        //System.out.println(response.body());

        String cookie = response.headers().allValues("set-cookie").get(0).split(";")[0];

        // post (login)

        Map<Object, Object> data = new HashMap<>();
        data.put("username", args[0]);
        data.put("password", args[1]);
        data.put("signIn", "signIn");

        String origin = "https://asismetro.org/";
        String nextUrl = "https://asismetro.org/index.php";

        HttpRequest requestPost = HttpRequest.newBuilder()
                .POST(buildFormDataFromMap(data))
                .uri(URI.create(nextUrl))
                .setHeader("User-Agent", userAgent)
                .header("Content-Type", contentType)
                .header("DNT", "1")
                .header("Origin", origin)
                .header("Cookie", cookie)
                .build();

        HttpResponse<String> responsePost = httpClient.send(requestPost, HttpResponse.BodyHandlers.ofString());

        System.out.println("Login: " + responsePost.statusCode());

        origin = "".concat(nextUrl);
        nextUrl = "https://asismetro.org/t_colaboraciones_ppam_view.php";
        // post navigate to Colaboraciones and filter by IFEMA

        Set<Integer> brothersColab = new HashSet<>();

        String stringPattern = "document.myform.SelectedID.value='(?<id>[0-9]+)'";

        int i = 1;
        int currentRecord = 1;
        int totalPages = 1;
        do {
            data.clear();
            data.put("current_view", "TV");
            data.put("SearchString", "ifema");
            //data.put("Search_x", "1");
            data.put("FirstRecord", currentRecord); // first page = 1, consecutive with +20
            data.put("NoDV", "1");
            data.put("DisplayRecords", "all");

            requestPost = HttpRequest.newBuilder()
                    .POST(buildFormDataFromMap(data))
                    .uri(URI.create(nextUrl))
                    .setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("DNT", "1")
                    .header("Origin", origin)
                    .header("Referer", origin)
                    .header("Cookie", cookie)
                    .build();

            responsePost = httpClient.send(requestPost, HttpResponse.BodyHandlers.ofString());

            Pattern pattern = Pattern.compile(stringPattern);
            Matcher matcher = pattern.matcher(responsePost.body());

            while (matcher.find())
                brothersColab.add(Integer.parseInt(matcher.group("id")));

            String stringPatternPages = "Registro [0-9] de [0-9]+ a (?<total>[0-9]+)";
            pattern = Pattern.compile(stringPatternPages);
            matcher = pattern.matcher(responsePost.body());

            while (matcher.find()) {
                Double totalPagesDouble = Math.ceil(Double.parseDouble(matcher.group("total")) / 20);
                totalPages = totalPagesDouble.intValue();
            }

            i++; //other page :)
            currentRecord += 20;

        } while (i <= totalPages);

        // post (get user id from colab id)

        Set<Integer> brotherIDs = new HashSet<>();

        for(Integer colabId : brothersColab) {
            data.clear();
            data.put("current_view", "TV");
            data.put("SearchString", "ifema");
            data.put("SelectedID", colabId);
            data.put("SelectedField", "1");
            data.put("FirstRecord", 1);
            data.put("NoDV", "1");
            data.put("DisplayRecords", "all");

            requestPost = HttpRequest.newBuilder()
                    .POST(buildFormDataFromMap(data))
                    .uri(URI.create(nextUrl))
                    .setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("DNT", "1")
                    .header("Origin", origin)
                    .header("Referer", origin)
                    .header("Cookie", cookie)
                    .build();

            responsePost = httpClient.send(requestPost, HttpResponse.BodyHandlers.ofString());

            stringPattern = "IdVoluntario\" value=\"(?<broId>[0-9]+)\"";
            Pattern pattern = Pattern.compile(stringPattern);
            Matcher matcher = pattern.matcher(responsePost.body());

            while (matcher.find())
                brotherIDs.add(Integer.parseInt(matcher.group("broId")));

            //break; // temp to test

        }

        // post (get details from Volunteer)
        origin = "https://asismetro.org";
        nextUrl = "https://asismetro.org/t_voluntarios_view.php";

        File file = new File("brothers.csv");
        FileWriter writer = new FileWriter(file);
        writer.write("Nombre,Email,Teléfono,Turno 1,Turno 2,Turno 3,Turno 4\n");

        for(Integer broId : brotherIDs) {
            data.clear();
            data.put("current_view", "TV");
            data.put("SearchString", "ifema");
            data.put("SelectedID", broId);
            data.put("SelectedField", "1");
            data.put("SortDirection", "desc");
            data.put("FirstRecord", 1);
            data.put("DisplayRecords", "all");

            requestPost = HttpRequest.newBuilder()
                    .POST(buildFormDataFromMap(data))
                    .uri(URI.create(nextUrl))
                    .setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("DNT", "1")
                    .header("Origin", origin)
                    .header("Referer", origin)
                    .header("Cookie", cookie)
                    .build();

            responsePost = httpClient.send(requestPost, HttpResponse.BodyHandlers.ofString());

            String namePattern = "\"NombreVoluntario\" value=\"(?<name>.+)\">";
            String name = "";
            Pattern pattern = Pattern.compile(namePattern);
            Matcher matcher = pattern.matcher(responsePost.body());
            while (matcher.find())
                name = matcher.group("name");

            String phonePattern = "\"TelefonoMovilVoluntario\" value=\"(?<phone>.+)\">";
            String phone= "";
            pattern = Pattern.compile(phonePattern);
            matcher = pattern.matcher(responsePost.body());
            while (matcher.find())
                phone = matcher.group("phone");

            String emailPattern = "\"MailVoluntario\" value=\"(?<email>.+)\">";
            String email = "";
            pattern = Pattern.compile(emailPattern);
            matcher = pattern.matcher(responsePost.body());
            while (matcher.find())
                email = matcher.group("email");

            String ppamPattern = "id=\"IdPPAM\" value=\"(?<ppam>[0-9]+)\">";
            String ppam = "";
            pattern = Pattern.compile(ppamPattern);
            matcher = pattern.matcher(responsePost.body());
            while (matcher.find()) {
                String ppamId = matcher.group("ppam");

                request = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(String.format("https://asismetro.org/ajax_combo.php?id=%s&t=t_voluntarios&f=IdPPAM", ppamId)))
                        .setHeader("User-Agent", userAgent)
                        .header("Cookie", cookie)
                        .header("DNT", "1")
                        .header("Origin", origin)
                        .header("Referer", origin)
                        .build();

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONArray ja = new JSONObject(response.body()).getJSONArray("results");
                JSONObject jo = ja.getJSONObject(0);
                ppam = jo.getString("text");

            }

            Brother brother = Brother.builder()
                    .name(name)
                    .phone(phone)
                    .email(email)
                    .ppam(ppam)
                    .build();

            StringBuilder shiftInfo = new StringBuilder();

            // post (get availability from volunteer)
            // call : https://asismetro.org/parent-children.php
            // origin: https://asismetro.org
            // referer: https://asismetro.org/t_voluntarios_view.php

            int actualPage = 1;
            int pages = 1;

            do {
                data.clear();
                data.put("ChildTable", "t_disponibilidades_voluntarios");
                data.put("ChildLookupField", "IdVoluntario");
                data.put("SelectedID", broId); //100480);
                data.put("Page", actualPage);
                data.put("SortBy", 1);
                data.put("SortDirection", "asc");
                data.put("Operation", "get-records");

                requestPost = HttpRequest.newBuilder()
                        .POST(buildFormDataFromMap(data))
                        .uri(URI.create("https://asismetro.org/parent-children.php"))
                        .setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("DNT", "1")
                        .header("Origin", "https://asismetro.org")
                        .header("Referer", "https://asismetro.org/t_voluntarios_view.php")
                        .header("Cookie", cookie)
                        .build();

                responsePost = httpClient.send(requestPost, HttpResponse.BodyHandlers.ofString());

                if (!responsePost.body().contains("No hay registros")) {
                    //System.out.println(getDaysFromResponse(responsePost.body()));
                    shiftInfo.append(responsePost.body()).append("\n");

                    if(pages == 1) { // execute just one time
                        String disponibilidadPattern = "Registro [0-9] de [0-9]+ a (?<total>[0-9]+)";
                        pattern = Pattern.compile(disponibilidadPattern);
                        matcher = pattern.matcher(responsePost.body());
                        while (matcher.find()) {
                            Double totalPagesDouble = Math.ceil(Double.parseDouble(matcher.group("total")) / 10);
                            pages = totalPagesDouble.intValue();
                            System.out.println(pages);
                        }
                    }
                }

                actualPage++;

            } while (actualPage <= pages);

            brother.setShifts(getDaysFromResponse(shiftInfo.toString()));
            brothers.add(brother);

            System.out.println(brother);

            // write on file
            writer.write(String.format("%s,%s,%s,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    brother.getName(),
                    brother.getEmail(),
                    brother.getPhone(),
                    brother.getShifts().get("Turno 1"),
                    brother.getShifts().get("Turno 2"),
                    brother.getShifts().get("Turno 3"),
                    brother.getShifts().get("Turno 4")
                    ));


        }

        writer.flush();
        writer.close();

        System.out.println("\n--- Finished! ---");

    }

    private static Map<String, String> getDaysFromResponse(String body) {
        Elements ls = Jsoup.parse(body).select("td.t_disponibilidades_voluntarios-IdPPAM, td.t_disponibilidades_voluntarios-IdDiaSemana, td.t_disponibilidades_voluntarios-IdTurnoDisponible");
        StringBuilder csv = new StringBuilder();
        for(Element element : ls) {
            csv.append(element.text()).append(",");
        }
        String ifemaShiftPattern = "IFEMA,(Lunes|Martes|Miércoles|Jueves|Viernes|Sábado|Domingo),Turno [1-4]";
        Pattern pattern = Pattern.compile(ifemaShiftPattern);
        Matcher matcher = pattern.matcher(csv.toString());

        Map<String, String> shifts = new TreeMap<>();
        shifts.put("Turno 1", "0000000");
        shifts.put("Turno 2", "0000000");
        shifts.put("Turno 3", "0000000");
        shifts.put("Turno 4", "0000000");
        String days ="LMXJVSD";

        while (matcher.find()) {
            String[] shift = matcher.group().split(",");
            String initial = getInitialFromDay(shift[1]);
            int dayPosition = days.indexOf(initial);
            StringBuilder shiftString = new StringBuilder(shifts.get(shift[2]));
            shiftString.setCharAt(dayPosition, initial.toCharArray()[0]);
            shifts.replace(shift[2], shiftString.toString());
        }

        shifts.replace("Turno 1", String.join(",",shifts.get("Turno 1").replace("0", "").split("")));
        shifts.replace("Turno 2", String.join(",",shifts.get("Turno 2").replace("0", "").split("")));
        shifts.replace("Turno 3", String.join(",",shifts.get("Turno 3").replace("0", "").split("")));
        shifts.replace("Turno 4", String.join(",",shifts.get("Turno 4").replace("0", "").split("")));

        System.out.println(shifts);
        return shifts;

    }

    private static String getInitialFromDay(String day){
        switch (day) {
            case "Lunes"     -> { return "L"; }
            case "Martes"    -> { return "M"; }
            case "Miércoles" -> { return "X"; }
            case "Jueves"    -> { return "J"; }
            case "Viernes"   -> { return "S"; }
            case "Sábado"    -> { return "V"; }
            case "Domingo"   -> { return "D"; }
        }
        return "";
    }

    private static HttpRequest.BodyPublisher buildFormDataFromMap(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        System.out.println(builder.toString());
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }
}
