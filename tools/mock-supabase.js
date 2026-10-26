// 模擬 Supabase PostgREST 的 POST /rest/v1/tree_measurements
// 用法: node mock-supabase.js   (預設 port 8787)
const http = require("http");
const seen = new Map();
const PORT = process.env.PORT || 8787;

http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    console.log(`\n${req.method} ${req.url}`);
    console.log("apikey:", req.headers["apikey"] ? "(有)" : "(缺)", "| Prefer:", req.headers["prefer"]);
    if (req.method === "GET" && req.url.startsWith("/rows")) {
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify([...seen.values()], null, 2));
    }
    if (req.method !== "POST" || !req.url.startsWith("/rest/v1/tree_measurements")) {
      res.writeHead(404); return res.end();
    }
    let row;
    try { row = JSON.parse(body); } catch { res.writeHead(400); return res.end("bad json"); }
    console.log(row);
    if (seen.has(row.id)) {
      console.log("→ 409 重複 id");
      res.writeHead(409, { "Content-Type": "application/json" });
      return res.end(JSON.stringify({ code: "23505", message: "duplicate key" }));
    }
    seen.set(row.id, row);
    console.log(`→ 201 (共 ${seen.size} 筆)`);
    res.writeHead(201); res.end();
  });
}).listen(PORT, "0.0.0.0", () => console.log(`mock supabase on http://0.0.0.0:${PORT}  (GET /rows 看已收資料)`));
