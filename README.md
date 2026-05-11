基于双路检索与 MCP 协议的智能 RAG-Agent

<img width="346" height="372" alt="image" src="https://github.com/user-attachments/assets/3ec215c8-abc3-4409-92bc-8547566d9d51" />

项目介绍：

1. 混合检索架构：BM25 + 向量双重增强

技术实现： 结合了传统词法检索（BM25）与现代深度学习语义检索（Vector Search），解决了单一向量检索在搜索特定产品型号、生僻编号或专业术语时精度不足的问题。

成果： 显著提升了 RAG 系统的检索召回率（Recall）与准确率（Precision），确保 AI 回复“有据可依”。



2. 智能化 Query 处理：语义重写引擎

技术实现： 引入 Query Rewrite 逻辑，利用 LLM 对用户模糊的意图（如“这家店怎么样？”）进行实体补全与去指代增强（如“XX 店铺的电脑质量与售后评价”）。



成果： 极大地改善了用户输入不确定性导致的检索失效问题，使检索命中率提升。



3. 生产级高可用：RabbitMQ 削峰与 Redis 语义缓存

异步解析： 针对大规模文档上传场景，引入 RabbitMQ 实现任务异步化与削峰填谷，避免解析与向量化任务阻塞主流程。



语义缓存： 利用 Redis 构建语义缓存层（Cache Hit Service），对高频重复请求直接返回结果，节省了约 30% 的 Token 成本，并降低了响应延迟。



4. 实时交互增强：MCP 协议与 Function Call 集成

动态扩展： 率先集成了 MCP (Model Context Protocol) 协议，允许 Agent 实时调用外部工具（如查询 Prometheus 告警、腾讯云日志、当前实时日期等）。



闭环交互： 实现从“纸上谈兵”到“现实联动”的跃迁，使 Agent 具备了处理实时运维数据与业务操作的能力。



5. 完整的全链路 RAG Pipeline

端到端实现： 覆盖了从多格式文档解析、动态切片（Chunking）、异步向量化入库（Milvus）到 RRF (Reciprocal Rank Fusion) 结果融合的全生命周期管理。



6.响应体验：实现流式输出，基于 SSE协议实现从底层 LLM 到前端界面的全链路流式数据传输。极大地降低了用户感知的首字响应时间，即使在 Agent 进行复杂检索和工具调用时，也能提供平滑、即时的交互体验。给予用户及时的反馈。
