package com.example.ytanalysis.client.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The response shape of {@code POST /api/v1/chat/completions} — the standard OpenAI chat
 * completion:
 *
 * <pre>{@code {
 *   "choices": [
 *     { "message": { "content": "the summary text", "role": "assistant" } }
 *   ]
 * }}</pre>
 *
 * <p>The Python code walked this with {@code choices[0]["message"]["content"]}. Here the
 * nested records let Jackson do that traversal declaratively: a {@code ChatResponse} has
 * {@code choices}, each {@code Choice} has a {@code message}, each {@code Message} has
 * {@code content}. Three records, one traversal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(java.util.List<Choice> choices) {

    /** The text of the first assistant message, or empty string if there is none. */
    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return "";
        }
        return choices.get(0).message().content();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {
    }
}