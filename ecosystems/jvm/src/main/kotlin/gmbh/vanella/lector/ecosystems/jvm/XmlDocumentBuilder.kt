package gmbh.vanella.lector.ecosystems.jvm

import org.xml.sax.helpers.DefaultHandler
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A parser for the XML that Maven and its plugins write. It rejects a DOCTYPE, which rules out external entities, and
 * keeps parse errors off stderr: they only arrive as the `SAXException`.
 */
internal fun xmlDocumentBuilder(): DocumentBuilder =
    DocumentBuilderFactory
        .newInstance()
        .apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        .apply { setErrorHandler(DefaultHandler()) }
