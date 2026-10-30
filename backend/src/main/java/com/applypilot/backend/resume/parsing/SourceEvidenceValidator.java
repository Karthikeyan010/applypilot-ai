package com.applypilot.backend.resume.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.text.Normalizer;
import java.util.*;

/** Evidence checking is validation, not a second extractor or rule-based fallback. */
public final class SourceEvidenceValidator {
    public List<ResumeParsingResult.ReviewWarning> validate(ObjectNode profile, String source) {
        var warnings = new ArrayList<ResumeParsingResult.ReviewWarning>();
        warnings.add(new ResumeParsingResult.ReviewWarning("profile", "AI draft: verify every field and the grouping of bullets against your source before confirming."));
        check(profile, "", normalize(source), warnings);
        profile.put("originalText", source);
        // Conservative fallback: never claim the model accounted for every source line.
        profile.put("unmatchedText", source);
        return List.copyOf(warnings);
    }
    private void check(JsonNode node, String path, String source, List<ResumeParsingResult.ReviewWarning> warnings) {
        if (node instanceof ObjectNode object) {
            var names = new ArrayList<String>(); object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                var child = object.get(name); String field = path.isEmpty() ? name : path + "." + name;
                if (child.isTextual() && !supported(child.asText(), source)) {
                    object.putNull(name);
                    warnings.add(new ResumeParsingResult.ReviewWarning(field, "Removed a value without a matching source passage. Review the original text and enter it manually if correct."));
                } else check(child, field, source, warnings);
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = array.size()-1; i >= 0; i--) {
                var child = array.get(i); String field = path + "[" + i + "]";
                if (child.isTextual() && !supported(child.asText(), source)) {
                    array.remove(i);
                    warnings.add(new ResumeParsingResult.ReviewWarning(field, "Removed an unsupported list item. Check the source for omitted details."));
                } else check(child, field, source, warnings);
            }
        }
    }
    private boolean supported(String text, String source) {
        String normalized = normalize(text);
        return !normalized.isBlank() && (" " + source + " ").contains(" " + normalized + " ");
    }
    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}+#@.]", " ").replaceAll("\\s+", " ").trim();
    }
}
