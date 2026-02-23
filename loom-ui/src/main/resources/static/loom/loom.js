(function() {
    let graphs = [];

    async function fetchGraphs() {
        try {
            const response = await fetch('/loom/api/graphs');
            graphs = await response.json();
            renderApiList();
            if (graphs.length > 0) {
                selectApi(0);
            }
        } catch (error) {
            console.error('Failed to fetch graphs:', error);
        }
    }

    function renderApiList() {
        const list = document.getElementById('api-list');
        list.innerHTML = '';

        graphs.forEach((api, index) => {
            const card = document.createElement('div');
            card.className = 'api-card';
            card.dataset.index = index;
            card.style.animationDelay = `${index * 0.05}s`;

            card.innerHTML = `
                <div class="card-row">
                    <span class="method method-${api.method}">${api.method}</span>
                    <span class="path">${api.path}</span>
                </div>
                <div class="card-meta">
                    <span class="type-badge">${api.type}</span>
                </div>
            `;
            card.addEventListener('click', () => selectApi(index));
            list.appendChild(card);
        });
    }

    function formatInterceptorName(name) {
        return name.replace(/Interceptor$/i, '');
    }

    function renderInterceptorPipeline(api) {
        const section = document.getElementById('interceptor-section');
        const pipeline = document.getElementById('interceptor-pipeline');
        const dagLabel = document.getElementById('dag-section-label');

        const interceptors = api.interceptors || [];

        if (interceptors.length === 0) {
            section.style.display = 'none';
            dagLabel.style.display = 'none';
            return;
        }

        section.style.display = 'block';
        dagLabel.style.display = 'block';
        pipeline.innerHTML = '';

        const sorted = [...interceptors].sort((a, b) => a.order - b.order);

        // Request node
        const reqNode = document.createElement('span');
        reqNode.className = 'interceptor-node node-request';
        reqNode.textContent = 'Request';
        reqNode.style.animationDelay = '0s';
        pipeline.appendChild(reqNode);

        sorted.forEach((interceptor, i) => {
            // Arrow
            const arrow = document.createElement('span');
            arrow.className = 'interceptor-arrow';
            arrow.textContent = '\u2192';
            pipeline.appendChild(arrow);

            // Interceptor node
            const node = document.createElement('span');
            node.className = 'interceptor-node node-interceptor';
            node.style.animationDelay = `${(i + 1) * 0.08}s`;
            node.innerHTML = `${formatInterceptorName(interceptor.name)} <span class="interceptor-order">#${interceptor.order}</span>`;
            pipeline.appendChild(node);
        });

        // Final arrow + DAG Execution node
        const finalArrow = document.createElement('span');
        finalArrow.className = 'interceptor-arrow';
        finalArrow.textContent = '\u2192';
        pipeline.appendChild(finalArrow);

        const dagNode = document.createElement('span');
        dagNode.className = 'interceptor-node node-dag';
        dagNode.style.animationDelay = `${(sorted.length + 1) * 0.08}s`;
        dagNode.textContent = 'DAG Execution';
        pipeline.appendChild(dagNode);
    }

    function selectApi(index) {
        document.querySelectorAll('.api-card').forEach(c => c.classList.remove('active'));
        const card = document.querySelector(`.api-card[data-index="${index}"]`);
        if (card) card.classList.add('active');

        const api = graphs[index];
        document.getElementById('current-api').textContent =
            `${api.method} ${api.path}` + (api.responseType ? ` \u2192 ${api.responseType}` : '');

        renderInterceptorPipeline(api);
        renderDag(api);
    }

    function renderDag(api) {
        const svg = d3.select('#dag-svg');
        svg.selectAll('*').remove();
        const inner = svg.append('g');

        const g = new dagreD3.graphlib.Graph().setGraph({
            rankdir: 'TB',
            marginx: 40,
            marginy: 40,
            ranksep: 60,
            nodesep: 50
        });

        api.nodes.forEach(node => {
            let cssClass = 'node-required';
            if (node.terminal) cssClass = 'node-terminal';
            else if (!node.required) cssClass = 'node-optional';

            let label = node.name;
            if (node.outputType) label += `\n\u2192 ${node.outputType}`;
            if (!node.required) label += '\n(optional)';

            g.setNode(node.name, {
                label: label,
                class: cssClass,
                shape: 'rect',
                padding: 14,
                width: 200,
                height: 60
            });
        });

        api.edges.forEach(edge => {
            g.setEdge(edge.from, edge.to, {
                arrowhead: 'vee',
                curve: d3.curveBasis
            });
        });

        const render = new dagreD3.render();
        render(inner, g);

        // Zoom and pan
        const zoom = d3.zoom().on('zoom', (event) => {
            inner.attr('transform', event.transform);
        });
        svg.call(zoom);

        // Auto-fit
        const bounds = inner.node().getBBox();
        const parent = svg.node().parentElement;
        const fullWidth = parent.clientWidth;
        const fullHeight = parent.clientHeight;
        const scale = Math.min(
            fullWidth / (bounds.width + 80),
            fullHeight / (bounds.height + 80),
            1.2
        );
        const translateX = (fullWidth - bounds.width * scale) / 2;
        const translateY = (fullHeight - bounds.height * scale) / 2;

        svg.call(zoom.transform, d3.zoomIdentity
            .translate(translateX, translateY)
            .scale(scale));
    }

    fetchGraphs();
})();
