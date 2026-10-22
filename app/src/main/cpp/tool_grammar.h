#pragma once
#include "llama-grammar.h"
#include "unicode.h"
#include <algorithm>
#include <memory>
#include <string>
#include <utility>
#include <vector>

namespace retrobit {
// Exercise the parser AND acceptance automaton without a model/vocabulary.
// This is not a substitute for actual inference on a target phone.
inline bool grammar_accepts(const std::string &source, const std::string &text) {
    std::unique_ptr<llama_grammar, decltype(&llama_grammar_free_impl)> grammar(
        llama_grammar_init_impl(nullptr, source.c_str(), "root", false, nullptr, 0, nullptr, 0),
        llama_grammar_free_impl);
    if (!grammar) return false;
    for (auto cp : unicode_cpts_from_utf8(text)) {
        llama_grammar_accept(grammar.get(), cp);
        if (grammar->stacks.empty()) return false;
    }
    return std::any_of(grammar->stacks.begin(), grammar->stacks.end(), [](const llama_grammar_stack &s) { return s.empty(); });
}
inline bool validate_tool_grammar(const std::string &source) {
    const std::vector<std::pair<std::string, bool>> cases = {
        {R"({"tool":"list_files","arguments":{}})", true},
        {"\n{\t\"message\" : \"Selesai\"\r}\n", true},
        {R"({"tool":"write_file","arguments":{"path":"a.js","content":"Halo 世界\n\"quoted\" \\ \u20ac /\t"}})", true},
        {R"({"tool":"create_table","arguments":{"name":"todo","columns":[{"name":"title","type":"TEXT"}],"seed_rows":2}})", true},
        {R"({"tool":"shell","arguments":{}})", false},
        {R"({"message":null})", false},
        {R"({"tool":"list_files","arguments":{})", false},
        {R"({"message":"x"}{"message":"y"})", false},
        {"{\"message\":\"bare\nnewline\"}", false},
        {"```json\n{\"message\":\"x\"}\n```", false},
        {"n{\"message\":\"x\"}", false},
        {"         {\"message\":\"x\"}", false},
    };
    for (const auto &test : cases) {
        if (grammar_accepts(source, test.first) != test.second) return false;
    }
    return true;
}
}
