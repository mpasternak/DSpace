/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.service.ClientInfoService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SpiderDetectorServiceImpl is used to find IP's that are spiders...
 * In future someone may add Host Domains
 * to the detection criteria here.
 *
 * @author kevinvandevelde at atmire.com
 * @author ben at atmire.com
 * @author Mark Diggory (mdiggory at atmire.com)
 * @author frederic at atmire.com
 */
public class SpiderDetectorServiceImpl implements SpiderDetectorService {

    private static final Logger log = LogManager.getLogger();

    private Boolean useCaseInsensitiveMatching;

    private final List<Pattern> agents
        = Collections.synchronizedList(new ArrayList<>());

    private final List<Pattern> domains
        = Collections.synchronizedList(new ArrayList<>());

    private final ConfigurationService configurationService;
    private final ClientInfoService clientInfoService;

    /**
     * Sparse HashTable structure to hold IP address ranges.
     */
    private IPTable table = null;

    @Autowired(required = true)
    public SpiderDetectorServiceImpl(ConfigurationService configurationService, ClientInfoService clientInfoService) {
        this.configurationService = configurationService;
        this.clientInfoService = clientInfoService;
    }

    @Override
    public IPTable getTable() {
        return table;
    }

    /**
     * Service Method for testing spiders against existing spider files.
     * <p>
     * In future spiders HashSet may be optimized as byte offset array to
     * improve performance and memory footprint further.
     *
     * @param clientIP address of the client.
     * @param proxyIPs comma-list of X-Forwarded-For addresses, or null.
     * @param hostname domain name of host, or null.
     * @param agent    User-Agent header value, or null.
     * @return true if the client matches any spider characteristics list.
     */
    @Override
    public boolean isSpider(String clientIP, String proxyIPs, String hostname, String agent) {
        // See if any agent patterns match
        if (null != agent) {
            synchronized (agents) {
                if (agents.isEmpty()) {
                    loadPatterns("agents", agents);
                }
            }

            if (isUseCaseInsensitiveMatching()) {
                agent = StringUtils.lowerCase(agent);
                hostname = StringUtils.lowerCase(hostname);
            }

            for (Pattern candidate : agents) {

                // prevent matcher() invocation from a null Pattern object
                if (null != candidate && candidate.matcher(agent).find()) {
                    return true;
                }


            }
        }

        // No.  See if any IP addresses match
        if (clientInfoService.isUseProxiesEnabled() && proxyIPs != null) {
            /* This header is a comma delimited list */
            for (String xfip : proxyIPs.split(",")) {
                if (isSpider(xfip)) {
                    return true;
                }
            }
        }

        if (isSpider(clientIP)) {
            return true;
        }

        // No.  See if any DNS names match
        if (null != hostname) {
            synchronized (domains) {
                if (domains.isEmpty()) {
                    loadPatterns("domains", domains);
                }
            }
            for (Pattern candidate : domains) {
                // prevent matcher() invocation from a null Pattern object
                if (null != candidate && candidate.matcher(hostname).find()) {
                    return true;
                }
            }
        }

        // Not a known spider.
        return false;
    }

    /**
     * Utility method which reads lines from a file & returns them in a Set.
     *
     * @param patternFile the location of our spider file
     * @return a vector full of patterns
     * @throws IOException could not happen since we check the file be4 we use it
     */
    @Override
    public Set<String> readPatterns(File patternFile)
        throws IOException {
        Set<String> patterns = new HashSet<>();

        if (!patternFile.exists() || !patternFile.isFile()) {
            return patterns;
        }

        //Read our file & get all them patterns.
        try (BufferedReader in = new BufferedReader(new FileReader(patternFile))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("#")) {
                    line = line.trim();

                    if (!line.equals("")) {
                        patterns.add(line);
                    }
                } else {
                    //   ua.add(line.replaceFirst("#","").replaceFirst("UA","").trim());
                    // ... add this functionality later
                }
            }
        }
        return patterns;
    }

    /**
     * Load agent name patterns from all files in a single subdirectory of config/spiders.
     *
     * @param directory   simple directory name (e.g. "agents").
     *                    "${dspace.dir}/config/spiders" will be prepended to yield the path to
     *                    the directory of pattern files.
     * @param patternList patterns read from the files in {@code directory} will
     *                    be added to this List.
     */
    private void loadPatterns(String directory, List<Pattern> patternList) {
        String dspaceHome = configurationService.getProperty("dspace.dir");
        File spidersDir = new File(dspaceHome, "config/spiders");
        File patternsDir = new File(spidersDir, directory);
        if (patternsDir.exists() && patternsDir.isDirectory()) {
            for (File file : patternsDir.listFiles()) {
                Set<String> patterns;
                try {
                    patterns = readPatterns(file);
                } catch (IOException ex) {
                    log.error("Patterns not read from {}:  {}",
                            file::getPath, ex::getMessage);
                    continue;
                }
                //If case insensitive matching is enabled, lowercase the patterns so they can be lowercase matched
                for (String pattern : patterns) {
                    if (isUseCaseInsensitiveMatching()) {
                        pattern = StringUtils.lowerCase(pattern);
                    }
                    patternList.add(Pattern.compile(pattern));
                }


                log.info("Loaded pattern file:  {}", file::getPath);
            }
        } else {
            log.info("No patterns loaded from {}", patternsDir::getPath);
        }
    }

    /**
     * Service Method for testing spiders against existing spider files.
     *
     * @param request
     * @return true|false if the request was detected to be from a spider.
     */
    @Override
    public boolean isSpider(HttpServletRequest request) {
        return isSpider(request.getRemoteAddr(),
                        request.getHeader("X-Forwarded-For"),
                        request.getRemoteHost(),
                        request.getHeader("User-Agent"));
    }

    /**
     * Check individual IP is a spider.
     *
     * @param ip
     * @return if is spider IP
     */
    @Override
    public boolean isSpider(String ip) {
        if (table == null) {
            loadSpiderIpAddresses();
        }

        try {
            if (table.contains(ip)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    /*
     *  loader to populate the table from files.
     */
    @Override
    public synchronized void loadSpiderIpAddresses() {

        if (table == null) {
            table = new IPTable();

            String filePath = configurationService.getProperty("dspace.dir");

            try {
                File spidersDir = new File(filePath, "config/spiders");

                if (spidersDir.exists() && spidersDir.isDirectory()) {
                    for (File file : spidersDir.listFiles()) {
                        if (file.isFile()) {
                            for (String ip : readPatterns(file)) {
                                log.debug("Loading {}", ip);
                                if (!Character.isDigit(ip.charAt(0))) {
                                    try {
                                        ip = DnsLookup.forward(ip);
                                        log.debug("Resolved to {}", ip);
                                    } catch (IOException e) {
                                        log.warn("Not loading {}:  {}", ip, e.getMessage());
                                        continue;
                                    }
                                }
                                table.add(ip);
                            }
                            log.info("Loaded Spider IP file: " + file);
                        }
                    }
                } else {
                    log.info("No spider file loaded");
                }
            } catch (IOException | IPTable.IPFormatException e) {
                log.error("Error Loading Spiders:" + e.getMessage(), e);
            }

        }

    }

    /**
     * checks if case insensitive matching is enabled
     *
     * @return true if it's enabled, false if not
     */
    private boolean isUseCaseInsensitiveMatching() {
        if (useCaseInsensitiveMatching == null) {
            try {
                useCaseInsensitiveMatching = configurationService
                    .getBooleanProperty("usage-statistics.bots.case-insensitive");
            } catch (ConversionException e) {
                useCaseInsensitiveMatching = false;
                log.warn("Please use a boolean value for usage-statistics.bots.case-insensitive");
            }
        }

        return useCaseInsensitiveMatching;
    }

}
