#!/usr/bin/env node
// Minimal MCP client for the Blockbench MCP Server plugin.
// Usage:
//   node tools/blockbench-mcp.mjs tools
//   node tools/blockbench-mcp.mjs call get_capabilities "{}"
//   node tools/blockbench-mcp.mjs call-file create_project args.json
//   node tools/blockbench-mcp.mjs eval "Project?.name"
//   node tools/blockbench-mcp.mjs eval-file path/to/script.js
//   node tools/blockbench-mcp.mjs screenshot
//
// Requires Blockbench running with the MCP plugin listening on localhost:3000.

import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.BB_MCP_URL || 'http://127.0.0.1:3000/bb-mcp';
let sessionId = null;
let nextId = 1;

function parseBody(text, contentType) {
  if (!text) return null;
  if ((contentType || '').includes('text/event-stream')) {
    const lines = text.split(/\r?\n/);
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim());
    const last = data.reverse().find(Boolean);
    return last ? JSON.parse(last) : null;
  }
  return JSON.parse(text);
}

async function post(body) {
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
  };
  if (sessionId) headers['Mcp-Session-Id'] = sessionId;
  const response = await fetch(BASE, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  const assigned = response.headers.get('mcp-session-id');
  if (assigned) sessionId = assigned;
  const text = await response.text();
  const json = parseBody(text, response.headers.get('content-type'));
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 500)}`);
  }
  return json;
}

async function initialize() {
  const init = await post({
    jsonrpc: '2.0',
    id: nextId++,
    method: 'initialize',
    params: {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'dsh-blockbench-client', version: '1.0' },
    },
  });
  if (init?.error) throw new Error(JSON.stringify(init.error));
  await post({ jsonrpc: '2.0', method: 'notifications/initialized' });
  return init.result;
}

async function request(method, params) {
  const result = await post({ jsonrpc: '2.0', id: nextId++, method, params });
  if (result?.error) throw new Error(JSON.stringify(result.error));
  return result?.result;
}

function printContent(result) {
  const content = result?.content || [];
  for (const item of content) {
    if (item.type === 'text') {
      console.log(item.text);
    } else if (item.type === 'resource_link') {
      console.log(`[resource] ${item.name} ${item.uri}`);
    } else if (item.type === 'image') {
      const ext = item.mimeType?.split('/')[1] || 'png';
      const out = path.join('art', `mcp-capture-${Date.now()}.${ext}`);
      fs.mkdirSync('art', { recursive: true });
      fs.writeFileSync(out, Buffer.from(item.data, 'base64'));
      console.log(`[image] ${out}`);
    } else {
      console.log(JSON.stringify(item));
    }
  }
  if (result?.isError) process.exitCode = 1;
}

async function callTool(name, args) {
  const result = await request('tools/call', { name, arguments: args });
  printContent(result);
  return result;
}

const command = process.argv[2] || 'tools';
try {
  await initialize();
  if (command === 'tools') {
    const result = await request('tools/list', {});
    for (const tool of result?.tools || []) {
      console.log(`${tool.name}\t${(tool.description || '').replace(/\s+/g, ' ').slice(0, 160)}`);
    }
  } else if (command === 'call') {
    const name = process.argv[3];
    if (!name) throw new Error('usage: call <tool-name> [json-arguments]');
    const args = process.argv[4] ? JSON.parse(process.argv[4]) : {};
    await callTool(name, args);
  } else if (command === 'call-file') {
    const name = process.argv[3];
    const file = process.argv[4];
    if (!name || !file) throw new Error('usage: call-file <tool-name> <json-file>');
    const args = JSON.parse(fs.readFileSync(file, 'utf8'));
    await callTool(name, args);
  } else if (command === 'eval') {
    const code = process.argv[3];
    if (!code) throw new Error('usage: eval <javascript>');
    await callTool('risky_eval', { code });
  } else if (command === 'eval-file') {
    const file = process.argv[3];
    if (!file) throw new Error('usage: eval-file <path>');
    const code = fs.readFileSync(file, 'utf8');
    await callTool('risky_eval', { code });
  } else if (command === 'screenshot') {
    await callTool('capture_app_screenshot', {});
  } else if (command === 'resources') {
    const result = await request('resources/list', {});
    for (const resource of result?.resources || []) {
      console.log(`${resource.uri}\t${resource.name || ''}`);
    }
  } else if (command === 'prompts') {
    const result = await request('prompts/list', {});
    for (const prompt of result?.prompts || []) {
      console.log(`${prompt.name}\t${prompt.description || ''}`);
    }
  } else {
    throw new Error(`unknown command: ${command}`);
  }
} catch (error) {
  console.error(error.message || String(error));
  process.exit(1);
}
