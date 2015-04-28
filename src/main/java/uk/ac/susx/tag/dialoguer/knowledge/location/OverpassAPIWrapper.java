package uk.ac.susx.tag.dialoguer.knowledge.location;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.xembly.Directives;
import org.xembly.ImpossibleModificationException;
import org.xembly.Xembler;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface to the Overpass API, a read-only API into open street maps.
 *
 * There are two main ways to call the API through this interface.
 *
 * 1. "getNearbyPlaceIDs" simply returns the open street map IDs of nearby places
 * 2. "queryAPI" returns a list of result objects, which represent the decoded JSON of the Overpass API response.
 *
 * User: Andrew D. Robertson
 * Date: 04/08/2014
 * Time: 13:33
 */
public class OverpassAPIWrapper implements AutoCloseable {

    private static final String overpassApi = "http://www.overpass-api.de/api/interpreter";

    private Client client;

    public OverpassAPIWrapper(){
        client = ClientBuilder.newClient();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public class ResultsWrapper {
        public String version;
        public String generator;
        public Map<String, String> osm3s;
        public List<ResultsElement> elements;
    }

    /**
     * Get a list of the IDs of the nearby shops and amentities
     */
    public List<Long> getNearbyPlaceIDs(double lat, double lon) throws IOException {
        return getNearbyPlaceIDs(lat, lon, 35, Sets.newHashSet("shop", "amenity"), null);
    }

    public List<Long> getNearbyPlaceIDs(double lat, double lon, double dist) throws IOException {
        return getNearbyPlaceIDs(lat, lon, dist, Sets.newHashSet("shop", "amenity"), null);
    }

    public ResultsElement getResultsElement(long openStreetMapID) throws IOException {
        return getResultsElement(openStreetMapID, client);
    }

    public static ResultsElement getResultsElement(long openStreetMapID, Client client) throws IOException {
        List<ResultsElement> results = queryAPI(buildIDQuery(openStreetMapID), client);
        results = RadiusAssigner.assignRadii(results);
        if (results.size() == 1) return results.get(0);
        else {
            List<ResultsElement> ways = new ArrayList<>();
            for (ResultsElement r : results){
                if (r.type.equals("way")) ways.add(r);
            }
            if (ways.size() == 1) return ways.get(0);
            else {
                throw new RuntimeException("Found multiple ways/nodes matching your OSM ID, this shouldn't happen...");
            }
        }
    }

    /**
     * Get the open street map IDs of nearby places in a bounding box centred on the user's location.
     *
     * @param lat user latitude
     * @param lon user longitude
     * @param dist the distance in metres from the user's location to a side of the bounding box (i.e. 10, would make a 20x20 box).
     * @param tagKeys Tags that if a place possesses, it will be part of the results (regardless of the value of the tag). E.g. "shop" returns all shops, and "amenity" returns all amenities.
     * @param tagKeyValuePairs Tag key-value pairs that should a place have, it will be part of the results. E.g. "shop, books" means that bookshops will be found.
     */
    public List<Long> getNearbyPlaceIDs(double lat, double lon, double dist, Set<String> tagKeys, Map<String,String> tagKeyValuePairs) throws IOException {
        List<ResultsElement> results = queryAPI(buildUnionQuery(lat,lon, dist, tagKeys, tagKeyValuePairs));
        List<Long> ids = new ArrayList<>();
        for (ResultsElement r : results){
            if (r.tags != null)
                ids.add(r.id);
        }
        return ids;
    }

    public List<Long> getNearbyPlaceIDs(List<ResultsElement> results){
        List<Long> ids = new ArrayList<>();
        for (ResultsElement r : results){
            if (r.tags != null) ids.add(r.id);
        }
        return ids;
    }

    /**
     * Get the full result objects back from the Overpass API for a given query.
     *
     * @param lat user latitude
     * @param lon user longitude
     * @param dist the distance in metres from the user's location to a side of the bounding box (i.e. 10, would make a 20x20 box).
     * @param tagKeys Tags that if a place possesses, it will be part of the results (regardless of the value of the tag). E.g. "shop" returns all shops, and "amenity" returns all amenities.
     * @param tagKeyValuePairs Tag key-value pairs that should a place have, it will be part of the results. E.g. "shop, books" means that bookshops will be found.
     */
    public List<ResultsElement> queryAPI(double lat, double lon, double dist, Set<String> tagKeys, Map<String, String> tagKeyValuePairs) throws IOException {
        return queryAPI(buildUnionQuery(lat, lon, dist, tagKeys, tagKeyValuePairs));
    }

    public List<ResultsElement> queryAPI(File xml) throws IOException {
        return queryAPI(Files.toString(xml, StandardCharsets.UTF_8));
    }

    /**
     * Send an XML query to the overpass API.
     *
     * You can use "buildUnionQuery" to formulate the XML.
     */
    public List<ResultsElement> queryAPI(String xml) {
        return queryAPI(xml, client);
    }

    public static List<ResultsElement> queryAPI(String xml, Client client){
        WebTarget target = client.target(overpassApi);

        Form form = new Form();
        form.param("data", xml);

        String results = target.request()
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);

        return decodeResults(results);
    }


    public static List<ResultsElement> decodeResults(String jsonResults) {
        return new Gson().fromJson(jsonResults, ResultsWrapper.class).elements;
    }

    public static List<ResultsElement> decodeResults(File jsonResults) throws FileNotFoundException, UnsupportedEncodingException {
        return new Gson().fromJson(new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(jsonResults), "UTF-8")),
                ResultsWrapper.class)
                .elements;
    }

    public static List<ResultsElement> decodeResults(InputStream jsonResults) throws UnsupportedEncodingException {
        return new Gson().fromJson(new BufferedReader(new InputStreamReader(jsonResults, "UTF-8")), ResultsWrapper.class).elements;
    }

    public static String buildUnionQuery(double lat, double lon, double distanceMetres, Set<String> tagKeys, Map<String, String> tagKeyValuePairs){

        double latD = Math.toDegrees(distanceMetres / 6378137); // earth's radius

        double north = lat + latD;
        double south = lat - latD;

        double lonD = Math.toDegrees(distanceMetres / (6378137 * Math.cos(Math.toRadians(lat))));
        double west = lon - lonD;
        double east = lon + lonD;

        return buildUnionQuery(north, south, west, east, tagKeys, tagKeyValuePairs);
    }

    public static String buildIDQuery(long id) {
        Directives d = new Directives();
        d.add("osm-script").attr("output", "json").attr("timeout", "900");

        d.add("union");
        d.add("id-query").attr("ref", Long.toString(id)).attr("type", "node").up();
        d.add("id-query").attr("ref", Long.toString(id)).attr("type", "way").up().up();

        d.add("print").attr("mode", "body").up();
        d.add("recurse").attr("type", "down").up();
        d.add("print").attr("mode", "skeleton").attr("order", "quadtile");

        try {
            return new Xembler(d).xml();
        } catch (ImpossibleModificationException e) {
            e.printStackTrace(); throw new RuntimeException(e);
        }
    }

    public static String buildUnionQuery(double north, double south, double west, double east, Set<String> tagKeys, Map<String, String> tagKeyValuePairs){

        Directives d = new Directives();

        d.add("osm-script").attr("output", "json").attr("timeout", "900");
        d.add("union");

        if (tagKeys != null){
            for (String tagKey : tagKeys){
                d.add("query").attr("type", "node")
                        .add("has-kv").attr("k", tagKey).up()
                        .add("bbox-query")
                        .attr("e", String.valueOf(east))
                        .attr("n", String.valueOf(north))
                        .attr("s", String.valueOf(south))
                        .attr("w", String.valueOf(west))
                        .up().up();

                d.add("query").attr("type", "way")
                        .add("has-kv").attr("k", tagKey).up()
                        .add("bbox-query")
                        .attr("e", String.valueOf(east))
                        .attr("n", String.valueOf(north))
                        .attr("s", String.valueOf(south))
                        .attr("w", String.valueOf(west))
                        .up().up();
            }
        }


        if (tagKeyValuePairs != null){
            for (Map.Entry<String, String> entry : tagKeyValuePairs.entrySet()){
                d.add("query").attr("type", "node")
                        .add("has-kv").attr("k", entry.getKey()).attr("v",entry.getValue()).up()
                        .add("bbox-query")
                        .attr("e", String.valueOf(east))
                        .attr("n", String.valueOf(north))
                        .attr("s", String.valueOf(south))
                        .attr("w", String.valueOf(west))
                        .up().up();

                d.add("query").attr("type", "way")
                        .add("has-kv").attr("k", entry.getKey()).attr("v",entry.getValue()).up()
                        .add("bbox-query")
                        .attr("e", String.valueOf(east))
                        .attr("n", String.valueOf(north))
                        .attr("s", String.valueOf(south))
                        .attr("w", String.valueOf(west))
                        .up().up();
            }
        }

        d.up();
        d.add("print").attr("mode", "body").up();
        d.add("recurse").attr("type", "down").up();
        d.add("print").attr("mode", "skeleton").attr("order", "quadtile");

        try {
            return new Xembler(d).xml();
        } catch (ImpossibleModificationException e) {
            e.printStackTrace(); throw new RuntimeException(e);
        }
    }
}
