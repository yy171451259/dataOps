import type * as monacoType from 'monaco-editor';

/** 数据库Schema信息 */
export interface SchemaInfo {
  name: string;
  columns: Array<{ name: string; type: string; key: string }>;
}

/** SQL关键字 */
const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'EXISTS', 'BETWEEN', 'LIKE', 'IS', 'NULL',
  'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN', 'FULL JOIN', 'CROSS JOIN', 'ON', 'USING',
  'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'UNION ALL', 'INTERSECT', 'EXCEPT',
  'INSERT INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE FROM', 'DELETE',
  'CREATE TABLE', 'ALTER TABLE', 'DROP TABLE', 'CREATE INDEX', 'DROP INDEX',
  'TRUNCATE TABLE', 'RENAME TABLE',
  'AS', 'DISTINCT', 'ALL', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
  'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'IFNULL', 'COALESCE', 'IF',
  'ASC', 'DESC', 'PRIMARY KEY', 'FOREIGN KEY', 'REFERENCES',
  'INT', 'INTEGER', 'BIGINT', 'SMALLINT', 'TINYINT', 'FLOAT', 'DOUBLE', 'DECIMAL',
  'VARCHAR', 'CHAR', 'TEXT', 'BLOB', 'DATE', 'DATETIME', 'TIMESTAMP', 'BOOLEAN',
  'TRUE', 'FALSE', 'DEFAULT', 'NOT NULL', 'AUTO_INCREMENT', 'UNIQUE', 'INDEX',
  'NOW', 'CURDATE', 'CURTIME', 'DATE_FORMAT', 'DATE_ADD', 'DATE_SUB', 'DATEDIFF',
  'CONCAT', 'SUBSTRING', 'LENGTH', 'TRIM', 'UPPER', 'LOWER', 'REPLACE',
  'CAST', 'CONVERT', 'EXPLAIN', 'SHOW', 'DESCRIBE', 'USE',
];

/** SQL聚合函数 */
const SQL_FUNCTIONS = [
  { label: 'COUNT', detail: '计数', insertText: 'COUNT(${1:column})' },
  { label: 'SUM', detail: '求和', insertText: 'SUM(${1:column})' },
  { label: 'AVG', detail: '平均值', insertText: 'AVG(${1:column})' },
  { label: 'MAX', detail: '最大值', insertText: 'MAX(${1:column})' },
  { label: 'MIN', detail: '最小值', insertText: 'MIN(${1:column})' },
  { label: 'GROUP_CONCAT', detail: '拼接', insertText: 'GROUP_CONCAT(${1:column})' },
  { label: 'IFNULL', detail: '空值替换', insertText: 'IFNULL(${1:expr}, ${2:value})' },
  { label: 'COALESCE', detail: '多值取非空', insertText: 'COALESCE(${1:expr1}, ${2:expr2})' },
  { label: 'CONCAT', detail: '字符串拼接', insertText: 'CONCAT(${1:str1}, ${2:str2})' },
  { label: 'SUBSTRING', detail: '子串', insertText: 'SUBSTRING(${1:str}, ${2:pos}, ${3:len})' },
  { label: 'NOW', detail: '当前时间', insertText: 'NOW()' },
  { label: 'CURDATE', detail: '当前日期', insertText: 'CURDATE()' },
  { label: 'DATE_FORMAT', detail: '日期格式化', insertText: 'DATE_FORMAT(${1:date}, ${2:format})' },
  { label: 'CAST', detail: '类型转换', insertText: 'CAST(${1:expr} AS ${2:type})' },
  { label: 'CASE WHEN', detail: '条件分支', insertText: 'CASE WHEN ${1:condition} THEN ${2:value} ELSE ${3:default} END' },
];

/** 解析SQL上下文，判断光标所在位置需要什么类型的补全 */
function analyzeSqlContext(
  textBeforeCursor: string,
  allText: string
): {
  context: 'table' | 'column' | 'dot' | 'keyword' | 'function' | 'alias_column' | 'unknown';
  prefix: string;
  tableAlias?: string;
  aliases: Map<string, string>;
} {
  const text = textBeforeCursor.trim().toUpperCase();
  const originalText = textBeforeCursor;

  // 提取表别名映射 (FROM table AS alias / FROM table alias)
  const aliases = extractTableAliases(allText);

  // 获取光标前的词（前缀）
  const wordMatch = originalText.match(/[\w.]*$/);
  const prefix = wordMatch ? wordMatch[0] : '';

  // 检查是否是 table.column 形式（点号补全）
  const dotMatch = originalText.match(/(\w+)\.\w*$/);
  if (dotMatch) {
    const aliasOrTable = dotMatch[1];
    return { context: 'dot', prefix, tableAlias: aliasOrTable, aliases };
  }

  // 检查是否在 FROM / JOIN / INTO / UPDATE / TABLE 后面 → 需要表名补全
  const tableContextPattern = /\b(FROM|JOIN|INTO|UPDATE|TABLE|TRUNCATE\s+TABLE|RENAME\s+TABLE)\s+\w*$/i;
  if (tableContextPattern.test(text) || tableContextPattern.test(originalText)) {
    return { context: 'table', prefix, aliases };
  }

  // 检查是否在 SELECT / WHERE / ON / AND / OR / BY / HAVING / SET 后面 → 需要列名补全
  const columnContextPattern = /\b(SELECT|WHERE|ON|AND|OR|BY|HAVING|SET|,)\s+\w*$/i;
  if (columnContextPattern.test(text) || columnContextPattern.test(originalText)) {
    return { context: 'column', prefix, aliases };
  }

  // 检查是否在函数调用位置（SELECT后、WHERE中的聚合函数）
  const funcPattern = /\b(SELECT|WHERE|AND|OR|HAVING|,)\s*\w*$/i;
  if (funcPattern.test(text) || funcPattern.test(originalText)) {
    // 同时提供列名和函数
    return { context: 'column', prefix, aliases };
  }

  return { context: 'keyword', prefix, aliases };
}

/** 从SQL文本中提取表别名 */
function extractTableAliases(sql: string): Map<string, string> {
  const aliases = new Map<string, string>();
  // 匹配 FROM table AS alias 或 FROM table alias (排除关键字)
  const keywords = new Set(['SELECT','FROM','WHERE','AND','OR','JOIN','LEFT','RIGHT','INNER','FULL','CROSS',
    'ON','GROUP','ORDER','HAVING','LIMIT','SET','INTO','VALUES','INSERT','UPDATE','DELETE','CREATE',
    'DROP','ALTER','TRUNCATE','AS','NOT','IN','EXISTS','BETWEEN','LIKE','IS','NULL','UNION','ALL',
    'DISTINCT','CASE','WHEN','THEN','ELSE','END','BY','ASC','DESC','OFFSET','USING']);

  // FROM table AS alias / JOIN table AS alias
  const patterns = [
    /(?:FROM|JOIN)\s+(\w+)\s+AS\s+(\w+)/gi,
    /(?:FROM|JOIN)\s+(\w+)\s+(\w+)(?=\s+(?:ON|WHERE|AND|OR|LEFT|RIGHT|INNER|FULL|CROSS|JOIN|GROUP|ORDER|HAVING|LIMIT|$))/gi,
  ];

  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(sql)) !== null) {
      const table = match[1];
      const alias = match[2];
      if (alias && !keywords.has(alias.toUpperCase()) && alias !== table) {
        aliases.set(alias, table);
      }
    }
  }

  // 也把表名本身作为 "别名" 加入
  return aliases;
}

/** 创建 Monaco SQL 补全提供器 */
export function createSqlCompletionProvider(
  getSchema: () => SchemaInfo[]
): monacoType.languages.CompletionItemProvider {
  return {
    triggerCharacters: ['.', ' '],
    provideCompletionItems: (
      model: monacoType.editor.ITextModel,
      position: monacoType.Position,
    ): monacoType.languages.CompletionList => {
      const textBeforeCursor = model.getValueInRange({
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });
      const allText = model.getValue();
      const schema = getSchema();

      const { context, tableAlias, aliases } = analyzeSqlContext(textBeforeCursor, allText);

      const word = model.getWordUntilPosition(position);
      const range: monacoType.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const suggestions: monacoType.languages.CompletionItem[] = [];

      switch (context) {
        case 'table': {
          // 提供表名
          for (const table of schema) {
            suggestions.push({
              label: table.name,
              kind: 5 /* Table */,
              detail: `表 (${table.columns.length} 列)`,
              insertText: table.name,
              range,
            });
          }
          break;
        }

        case 'dot': {
          // table.column 或 alias.column → 提供该表的列名
          const resolvedTable = resolveTableName(tableAlias!, schema, aliases);
          if (resolvedTable) {
            for (const col of resolvedTable.columns) {
              suggestions.push({
                label: col.name,
                kind: 10 /* Field */,
                detail: `${col.type}${col.key === 'PRI' ? ' 🔑' : ''}`,
                insertText: col.name,
                range,
              });
            }
          }
          break;
        }

        case 'column': {
          // 提供所有表的列名（带表名前缀区分）
          const addedNames = new Set<string>();
          for (const table of schema) {
            for (const col of table.columns) {
              if (!addedNames.has(col.name)) {
                addedNames.add(col.name);
                suggestions.push({
                  label: col.name,
                  kind: 10 /* Field */,
                  detail: `${table.name}.${col.name} (${col.type})${col.key === 'PRI' ? ' 🔑' : ''}`,
                  insertText: col.name,
                  range,
                });
              }
            }
            // 同时提供表名（可能用于子查询等）
            suggestions.push({
              label: table.name,
              kind: 5 /* Table */,
              detail: `表 (${table.columns.length} 列)`,
              insertText: table.name,
              range,
            });
          }
          // 提供常用函数
          addFunctionSuggestions(suggestions, range);
          break;
        }

        case 'keyword':
        default: {
          // SQL关键字
          for (const kw of SQL_KEYWORDS) {
            suggestions.push({
              label: kw,
              kind: 14 /* Keyword */,
              insertText: kw,
              range,
            });
          }
          // 表名
          for (const table of schema) {
            suggestions.push({
              label: table.name,
              kind: 5 /* Table */,
              detail: `表 (${table.columns.length} 列)`,
              insertText: table.name,
              range,
            });
          }
          // 函数
          addFunctionSuggestions(suggestions, range);
          break;
        }
      }

      return { suggestions };
    },
  };
}

function addFunctionSuggestions(
  suggestions: monacoType.languages.CompletionItem[],
  range: monacoType.IRange
) {
  for (const fn of SQL_FUNCTIONS) {
    suggestions.push({
      label: fn.label,
      kind: 1 /* Function */,
      detail: fn.detail,
      insertText: fn.insertText,
      insertTextRules: 4 /* InsertAsSnippet */,
      range,
    });
  }
}

function resolveTableName(
  aliasOrTable: string,
  schema: SchemaInfo[],
  aliases: Map<string, string>
): SchemaInfo | undefined {
  // 先尝试直接匹配表名
  const direct = schema.find(t => t.name.toLowerCase() === aliasOrTable.toLowerCase());
  if (direct) return direct;

  // 再尝试通过别名解析
  const resolvedName = aliases.get(aliasOrTable) || aliases.get(aliasOrTable.toLowerCase());
  if (resolvedName) {
    return schema.find(t => t.name.toLowerCase() === resolvedName.toLowerCase());
  }

  return undefined;
}
