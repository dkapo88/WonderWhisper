package com.slumdog88.dictationkeyboardai

import java.util.*

/**
 * Data class representing a reformat prompt for AI content processing
 */
data class ReformatPrompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val promptText: String,
    val isDefault: Boolean = false
) {
    
    companion object {
        /**
         * Returns the default reformat prompts
         */
        fun getDefaultPrompts(): List<ReformatPrompt> {
            return listOf(
                // Auto-Detect - Comprehensive context analysis and intelligent formatting
                ReformatPrompt(
                    id = "auto_detect",
                    name = "Auto-Detect",
                    description = "Automatically detect content type and format appropriately based on context, structure, and intent",
                    promptText = """
                        You are an expert content analyst and formatter. Analyze the following content deeply to understand its nature, purpose, and optimal presentation format. Consider multiple contextual factors:

                        CONTENT ANALYSIS:
                        1. **Purpose & Intent**: Is this informational, persuasive, organizational, creative, analytical, or procedural?
                        2. **Content Type**: Meeting notes, email, brainstorm, presentation, instructions, ideas, feedback, planning, research, documentation?
                        3. **Audience**: Who will read this? (colleagues, managers, clients, partners, technical team, creative team?)
                        4. **Context**: Business, technical, creative, educational, personal, professional?
                        5. **Tone**: Formal, informal, technical, creative, urgent, reflective?

                        FORMATTING STRATEGIES BY CONTENT TYPE:

                        **MEETING/PLANNING CONTENT**:
                        - Extract: participants, agenda items, decisions, action items, timelines, next steps
                        - Structure: Meeting Summary, Attendees, Key Discussion Points, Decisions Made, Action Items with owners & deadlines, Next Steps
                        - Add: Priority levels, status indicators, follow-up reminders

                        **BRAINSTORM/IDEATION CONTENT**:
                        - Categorize: Group related ideas, eliminate duplicates, identify themes
                        - Structure: Main Concepts, Supporting Ideas, Action Items, Questions/Considerations, Priority Matrix
                        - Enhance: Add feasibility notes, resource requirements, potential impact

                        **EMAIL/COMMUNICATION CONTENT**:
                        - Structure: Clear subject line, greeting, context, main message, call-to-action, professional closing
                        - Enhance: Add appropriate tone, clear action items, follow-up expectations

                        **INSTRUCTIONAL/PROCEDURAL CONTENT**:
                        - Break down: Complex processes into numbered steps with clear actions
                        - Enhance: Add prerequisites, warnings, tips, troubleshooting
                        - Structure: Overview, Prerequisites, Step-by-step instructions, Verification, Next steps

                        **ANALYTICAL/EVALUATION CONTENT**:
                        - Structure: Executive Summary, Analysis, Findings, Recommendations, Implementation Plan
                        - Enhance: Add data organization, pros/cons analysis, risk assessment, success metrics

                        **CREATIVE/PRESENTATION CONTENT**:
                        - Structure: Introduction, Main Content, Key Messages, Supporting Details, Conclusion
                        - Enhance: Add storytelling elements, visual organization, key takeaways

                        **RESEARCH/DOCUMENTATION CONTENT**:
                        - Structure: Abstract/Summary, Background, Methodology, Findings, Conclusions, References
                        - Enhance: Add source citations, data organization, key insights highlighting

                        **STAND-UP/STATUS UPDATE CONTENT**:
                        - Structure: Yesterday's Progress, Today's Goals, Blockers/Help Needed, Key Metrics
                        - Enhance: Add priority indicators, timeline projections, risk flags

                        **GENERAL NOTES/THOUGHT DUMP**:
                        - Analyze patterns and themes
                        - Structure: Logical paragraphs with clear headings, bullet points for lists, numbered steps for processes
                        - Enhance: Add topic headers, transition sentences, summary points

                        PROCESSING RULES:
                        - Preserve all original meaning and important details
                        - Use professional, clear language appropriate to the context
                        - Add logical structure with headings and subheadings
                        - Include relevant metadata (dates, people, deadlines, priorities)
                        - Break up dense text into scannable sections
                        - Add action items, decisions, and next steps where applicable
                        - Use formatting elements (bold, italics, bullet points) strategically
                        - Ensure logical flow and readability
                        - Add appropriate emphasis and prioritization
                        - Include helpful context and background when relevant

                        Output ONLY the intelligently formatted content with no preamble, explanations, or additional commentary.

                        Content to analyze and format:
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Meeting Notes - Professional, comprehensive meeting documentation
                ReformatPrompt(
                    id = "meeting_notes",
                    name = "Meeting Notes",
                    description = "Professional meeting notes with participants, discussions, decisions, and action items",
                    promptText = """
                        Format the following content as comprehensive, professional meeting notes suitable for distribution and follow-up. Extract and organize all relevant information systematically.

                        REQUIRED SECTIONS (include only sections with relevant content):

                        ## 📅 Meeting Details
                        - **Date & Time**: [Extract or infer date/time]
                        - **Location/Platform**: [In-person, virtual platform, etc.]
                        - **Meeting Type**: [Regular sync, planning, review, brainstorming, etc.]

                        ## 👥 Participants
                        - **Attendees**: [List all mentioned participants]
                        - **Absentees**: [If mentioned]
                        - **Facilitator/Chair**: [If identifiable]
                        - **Note Taker**: [If mentioned]

                        ## 🎯 Objectives & Agenda
                        - **Primary Goal**: [Main purpose of the meeting]
                        - **Key Topics**: [Agenda items discussed]
                        - **Time Allocation**: [If mentioned]

                        ## 📝 Key Discussion Points
                        - Organize by topic with bullet points
                        - Include important details, opinions, and perspectives shared
                        - Note any debates or differing viewpoints
                        - Highlight critical information and decisions

                        ## ✅ Decisions Made
                        - **Final Decisions**: [Clear statements of what was decided]
                        - **Rationale**: [Why decisions were made, if explained]
                        - **Impact**: [How decisions affect projects/teams]

                        ## 📋 Action Items
                        - **Immediate Actions**: [Tasks needing immediate attention]
                        - **Short-term Tasks**: [Tasks with specific deadlines]
                        - **Long-term Initiatives**: [Ongoing projects or strategic items]
                        - **Owner Assignment**: [Who is responsible for each action]
                        - **Due Dates**: [Specific deadlines mentioned]

                        ## 🚧 Blockers & Challenges
                        - **Current Obstacles**: [Issues needing resolution]
                        - **Dependencies**: [External factors affecting progress]
                        - **Resource Needs**: [Additional resources required]

                        ## 📊 Key Metrics & Data
                        - **Performance Indicators**: [KPIs, metrics, or data points discussed]
                        - **Progress Updates**: [Status of ongoing projects]
                        - **Success Measures**: [How success will be measured]

                        ## 📅 Next Steps & Timeline
                        - **Follow-up Actions**: [Specific next steps with owners and dates]
                        - **Upcoming Milestones**: [Important dates and deadlines]
                        - **Future Meetings**: [Scheduled follow-ups or related meetings]

                        ## 💭 Additional Notes
                        - **Open Questions**: [Items requiring further discussion]
                        - **Parking Lot Items**: [Topics tabled for later]
                        - **Key Insights**: [Important learnings or observations]

                        FORMATTING GUIDELINES:
                        - Use clear, professional language
                        - Be comprehensive but concise
                        - Use bullet points and sub-bullets for readability
                        - Include status indicators: ✅ (completed), 🔄 (in progress), ⏳ (pending), 🚨 (urgent)
                        - Add priority levels: 🔥 (high), 📍 (medium), 📝 (low)
                        - Bold important names, dates, and action items
                        - Group related information logically
                        - Include timestamps where relevant

                        Output ONLY the formatted meeting notes with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Simple Reformat - Enhanced grammar and structure
                ReformatPrompt(
                    id = "simple_reformat",
                    name = "Simple Reformat",
                    description = "Comprehensive cleanup with grammar, structure, and clarity improvements",
                    promptText = """
                        Perform a comprehensive cleanup and enhancement of the following content while preserving the original meaning and intent. Focus on clarity, professionalism, and optimal structure.

                        GRAMMAR & LANGUAGE IMPROVEMENTS:
                        - Correct all spelling, grammar, and punctuation errors
                        - Improve sentence structure and flow for better readability
                        - Enhance vocabulary while maintaining original tone and voice
                        - Eliminate redundancy, filler words, and unnecessary repetition
                        - Ensure consistent tense, voice, and style throughout
                        - Add transitions between ideas for better coherence
                        - Break up run-on sentences and improve clarity

                        STRUCTURAL ENHANCEMENTS:
                        - Organize content into logical paragraphs with clear topic sentences
                        - Add appropriate headings and subheadings where content suggests natural divisions
                        - Create smooth transitions between sections
                        - Ensure logical progression of ideas
                        - Break up dense blocks of text for better readability
                        - Use formatting elements (bullet points, numbered lists) where appropriate

                        CONTENT OPTIMIZATION:
                        - Maintain all original facts, details, and important information
                        - Clarify ambiguous statements while preserving original meaning
                        - Strengthen weak arguments or unclear explanations
                        - Improve the hierarchy and emphasis of information
                        - Ensure professional, clear, and concise expression
                        - Add context or clarification where needed for understanding

                        FORMATTING GUIDELINES:
                        - Use consistent formatting and styling
                        - Add emphasis (bold, italics) for important terms or key points
                        - Use bullet points for lists and key information
        - Break content into scannable sections
        - Ensure appropriate line spacing and paragraph breaks
        - Maintain the original voice and personality while improving clarity

        QUALITY ASSURANCE:
        - Verify all original information is preserved
        - Ensure the enhanced version flows naturally and reads well
        - Confirm improved clarity without adding new information
        - Check for consistent tone and professional presentation

        Output ONLY the enhanced, reformatted content with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Brainstorm Organizer - Comprehensive idea categorization and prioritization
                ReformatPrompt(
                    id = "brainstorm_organize",
                    name = "Brainstorm Organizer",
                    description = "Intelligent categorization, prioritization, and organization of brainstormed ideas",
                    promptText = """
                        Analyze and organize the following brainstormed content into a comprehensive, actionable structure. Be intelligent about grouping, prioritizing, and enhancing the ideas while preserving all original concepts.

                        ANALYSIS PHASE:
                        1. **Identify Themes**: Group related ideas and concepts
                        2. **Eliminate Duplicates**: Remove redundant or very similar ideas
                        3. **Find Connections**: Note relationships between different ideas
                        4. **Assess Feasibility**: Consider practical implementation factors
                        5. **Determine Impact**: Evaluate potential value and outcomes

                        ORGANIZATION STRUCTURE:

                        ## 🎯 Core Concepts & Big Ideas
                        - **Primary Ideas**: Main concepts and overarching themes
        - **Unique Insights**: Novel or breakthrough thinking
        - **Problem Solutions**: Ideas that address specific challenges
        - **Innovation Opportunities**: Creative or disruptive concepts

        ## 📋 Detailed Implementation Ideas
        - **Specific Actions**: Concrete steps and tactics
        - **Process Improvements**: Ways to enhance existing workflows
        - **Feature Enhancements**: Product or service improvements
        - **Communication Strategies**: Messaging and outreach ideas

        ## 💡 Supporting Details & Examples
        - **Use Cases**: Specific scenarios or applications
        - **Success Stories**: Examples of similar successful implementations
        - **Data Points**: Relevant metrics, research, or evidence
        - **Technical Considerations**: Implementation requirements

        ## ⚡ Quick Wins & Immediate Actions
        - **Low-Hanging Fruit**: Easy-to-implement ideas with high impact
        - **Fast Experiments**: Ideas that can be tested quickly
        - **Pilot Opportunities**: Small-scale trial implementations

        ## 🚀 Strategic Initiatives & Long-term Vision
        - **Big Bets**: High-risk, high-reward opportunities
        - **Growth Opportunities**: Ideas that could scale significantly
        - **Future Vision**: Forward-thinking concepts for long-term planning

        ## ❓ Questions & Further Exploration
        - **Clarifying Questions**: Items needing more information or research
        - **Risk Considerations**: Potential obstacles or challenges to address
        - **Dependencies**: External factors or prerequisites
        - **Success Metrics**: How to measure if an idea works

        ## 🔥 Priority Matrix
        - **High Priority**: 🔥 Critical path items and immediate opportunities
        - **Medium Priority**: 📍 Important but not urgent items
        - **Low Priority**: 📝 Nice-to-have items and future considerations
        - **On Hold**: ⏸️ Items requiring more information or resources

        FORMATTING ENHANCEMENTS:
        - Use clear visual hierarchy with headings and subheadings
        - Add priority indicators and status markers
        - Group related ideas under thematic categories
        - Include implementation difficulty estimates
        - Add resource or time requirement notes
        - Highlight innovative or unique concepts
        - Create logical flow between categories
        - Use bullet points and numbering for clarity
        - Add cross-references between related ideas

        QUALITY ASSURANCE:
        - Ensure no original ideas are lost in the organization
        - Create logical, intuitive groupings
        - Balance comprehensive coverage with readability
        - Maintain the creative energy of the original brainstorming

        Output ONLY the organized and enhanced brainstorm content with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Professional Email - Complete email formatting with context awareness
                ReformatPrompt(
                    id = "professional_email",
                    name = "Professional Email",
                    description = "Complete professional email with subject, greeting, structure, and appropriate tone",
                    promptText = """
                        Format the following content as a complete, professional business email. Analyze the context to determine the appropriate tone, structure, and level of formality.

                        EMAIL ANALYSIS:
                        - **Purpose**: What is the main goal? (Inform, request, follow-up, propose, update, etc.)
                        - **Audience**: Who is the recipient? (Colleague, manager, client, partner, etc.)
                        - **Relationship**: What is the professional relationship and appropriate tone?
                        - **Urgency**: How time-sensitive is this communication?
                        - **Complexity**: How much detail and context is needed?

                        EMAIL STRUCTURE:

                        **SUBJECT LINE**
        - Create a clear, specific subject line that summarizes the email's purpose
        - Use action-oriented language where appropriate
        - Keep under 50 characters for mobile compatibility
        - Include key identifiers (project names, deadlines, etc.) if relevant

        **SALUTATION**
        - Use appropriate greeting based on relationship and formality
        - Professional options: "Dear [Name],", "Hello [Name],", "Hi [Name],"
        - Group options: "Dear Team,", "Hello Everyone,", "Hi All,"
        - Consider cultural context and organizational norms

        **OPENING/CONTEXT**
        - Provide brief context or reference to previous communication
        - State the purpose clearly and concisely
        - Set appropriate expectations for the email's content
        - Build rapport if relationship allows

        **MAIN BODY**
        - Organize content logically with clear paragraphs
        - Use professional, clear language appropriate to the audience
        - Include all necessary details, background, and supporting information
        - Structure complex information with bullet points or numbered lists
        - Highlight key points, action items, or important information
        - Maintain professional tone while being personable when appropriate

        **CALL TO ACTION**
        - Clearly state what action is needed from the recipient
        - Include specific deadlines or timeframes when relevant
        - Provide clear next steps or response expectations
        - Make it easy for the recipient to take the desired action

        **CLOSING**
        - Use professional sign-off appropriate to the relationship
        - Options: "Best regards,", "Sincerely,", "Thank you,", "Looking forward to your response,"
        - Include professional signature with relevant contact information
        - Add any relevant links, attachments, or additional resources

        ENHANCEMENT GUIDELINES:
        - Ensure professional tone throughout while maintaining warmth where appropriate
        - Use active voice and clear, concise language
        - Include all necessary context and background information
        - Structure information for easy scanning and comprehension
        - Add appropriate emphasis and formatting for key information
        - Consider the recipient's perspective and information needs
        - Include relevant attachments, links, or references
        - Add follow-up information if the topic requires ongoing communication

        QUALITY ASSURANCE:
        - Verify all original information and requests are included
        - Ensure logical flow and professional presentation
        - Confirm appropriate level of detail for the audience and purpose
        - Check for clear call-to-action and next steps

        Output ONLY the complete, formatted professional email with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Todo List - Action-oriented task organization
                ReformatPrompt(
                    id = "todo_list",
                    name = "Todo List",
                    description = "Convert content into actionable tasks with priorities, owners, and deadlines",
                    promptText = """
                        Convert the following content into a comprehensive, actionable todo list. Analyze the content to extract all tasks, responsibilities, and action items, then organize them systematically.

                        TASK ANALYSIS:
                        1. **Extract Actions**: Identify all verbs and action-oriented language
                        2. **Find Responsibilities**: Note who is responsible for each task
                        3. **Determine Deadlines**: Look for dates, timeframes, or urgency indicators
                        4. **Assess Dependencies**: Identify tasks that depend on others
                        5. **Evaluate Priority**: Determine relative importance and urgency

                        ORGANIZATION STRUCTURE:

                        ## 🔥 HIGH PRIORITY TASKS
                        - **Immediate Actions**: Tasks needing immediate attention
                        - **Critical Path Items**: Tasks blocking other progress
                        - **Time-Sensitive Tasks**: Tasks with approaching deadlines

                        ## 📍 MEDIUM PRIORITY TASKS
                        - **Important Tasks**: Significant tasks without immediate deadlines
                        - **Development Items**: Tasks contributing to larger goals
                        - **Improvement Initiatives**: Enhancement and optimization tasks

                        ## 📝 LOW PRIORITY TASKS
                        - **Future Considerations**: Tasks for later planning
                        - **Nice-to-Have Items**: Optional improvements and enhancements
                        - **Research Tasks**: Items requiring further investigation

                        ## 🔄 ONGOING TASKS
                        - **Recurring Activities**: Regular tasks and maintenance
                        - **Monitoring Tasks**: Items requiring ongoing attention
                        - **Review Tasks**: Periodic check-ins and assessments

                        ## ⏸️ ON HOLD TASKS
                        - **Waiting Tasks**: Tasks dependent on external factors
        - **Pending Tasks**: Tasks awaiting more information or resources
        - **Future Tasks**: Items scheduled for later implementation

        TASK FORMATTING:
        - **Task Description**: Clear, specific, actionable description
        - **Owner**: Person responsible (if identifiable)
        - **Due Date**: Specific deadline (if mentioned or inferable)
        - **Priority Level**: 🔥 (High), 📍 (Medium), 📝 (Low)
        - **Status**: ✅ (Completed), 🔄 (In Progress), ⏳ (Pending), 🚨 (Urgent)
        - **Dependencies**: Other tasks or factors required first
        - **Estimated Time**: Time required (if inferable)
        - **Resources Needed**: Additional resources or support required

        ENHANCEMENT FEATURES:
        - Break complex tasks into specific, measurable sub-tasks
        - Add context and background where helpful
        - Include success criteria or completion indicators
        - Add relevant links, references, or resources
        - Group related tasks under project or thematic categories
        - Add progress tracking indicators where appropriate
        - Include contact information for task owners
        - Add notes for clarification or additional context

        QUALITY ASSURANCE:
        - Ensure all original tasks and information are captured
        - Make tasks specific and measurable where possible
        - Organize logically by priority and timeline
        - Include all necessary context and details
        - Make the list comprehensive yet manageable

        Output ONLY the formatted, organized todo list with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Executive Summary - Strategic overview with key insights
                ReformatPrompt(
                    id = "executive_summary",
                    name = "Executive Summary",
                    description = "Strategic executive summary with key insights, decisions, and recommendations",
                    promptText = """
                        Create a comprehensive executive summary that captures the essence, key insights, and strategic implications of the following content. Focus on what senior leaders need to know and decide.

                        EXECUTIVE SUMMARY FRAMEWORK:

                        ## 🎯 Executive Overview
                        - **Core Message**: The single most important point or conclusion
                        - **Key Context**: Essential background information for understanding
        - **Time Horizon**: Immediate, short-term, long-term implications
        - **Stakeholders**: Who needs to know and why

        ## 📊 Key Findings & Insights
        - **Critical Data Points**: Most important metrics, results, or discoveries
        - **Major Trends**: Significant patterns or developments identified
        - **Success Indicators**: What went well and why
        - **Risk Factors**: Potential challenges or concerns identified
        - **Unexpected Insights**: Surprising findings or new perspectives

        ## 💡 Strategic Implications
        - **Business Impact**: How this affects strategic objectives
        - **Competitive Position**: Impact on market position or advantage
        - **Resource Implications**: Resource needs or reallocations required
        - **Timeline Considerations**: Important dates and milestones
        - **Dependency Factors**: Critical dependencies or prerequisites

        ## ✅ Key Decisions & Actions Required
        - **Immediate Decisions**: Choices that must be made now
        - **Strategic Directions**: Major direction-setting decisions
        - **Resource Allocations**: Budget, people, or asset decisions
        - **Policy Changes**: New policies or process changes needed
        - **Communication Needs**: What and to whom needs to be communicated

        ## 🚀 Recommendations & Next Steps
        - **Priority Actions**: Most important steps to take immediately
        - **Implementation Plan**: High-level plan for moving forward
        - **Success Metrics**: How to measure success and progress
        - **Milestone Timeline**: Key dates and checkpoints
        - **Accountability**: Who owns what actions

        ## ⚠️ Critical Risks & Mitigations
        - **High-Risk Items**: Potential obstacles or threats
        - **Contingency Plans**: Backup approaches if needed
        - **Early Warning Signs**: What to watch for going forward
        - **Risk Mitigation**: Actions to reduce identified risks

        EXECUTIVE WRITING PRINCIPLES:
        - **Conciseness**: Every word serves a purpose
        - **Clarity**: Complex ideas explained simply and directly
        - **Context**: Enough background for informed decision-making
        - **Priority**: Most important information first
        - **Actionability**: Clear next steps and accountability
        - **Objectivity**: Facts and analysis separated from opinions
        - **Strategic Focus**: Business impact and implications emphasized

        FORMATTING GUIDELINES:
        - Use bullet points and clear headings for scannability
        - Bold key terms, names, dates, and critical information
        - Use priority indicators: 🔥 (critical), 📍 (important), 📝 (informational)
        - Include executive summary length appropriate to content complexity
        - Structure for quick reading while maintaining comprehensive coverage
        - Add visual separators and emphasis for key points
        - Use professional, confident language appropriate for executive audience

        Output ONLY the executive summary with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Step-by-Step Guide - Clear, comprehensive instructions
                ReformatPrompt(
                    id = "step_by_step",
                    name = "Step-by-Step Guide",
                    description = "Clear, numbered step-by-step instructions with prerequisites and troubleshooting",
                    promptText = """
                        Convert the following content into a comprehensive, user-friendly step-by-step guide. Break down complex processes into clear, actionable steps while preserving all important information.

                        GUIDE ANALYSIS & PLANNING:
                        1. **Identify Process**: What is the overall process or task?
                        2. **Determine Prerequisites**: What knowledge, tools, or preparation is needed?
                        3. **Map Dependencies**: What steps depend on previous steps?
                        4. **Assess Complexity**: How detailed should the instructions be?
                        5. **Define Success**: What does successful completion look like?

                        GUIDE STRUCTURE:

                        ## 🎯 Overview & Prerequisites
                        - **Goal**: What will be accomplished
        - **Time Required**: Estimated time to complete
        - **Skill Level**: Beginner, Intermediate, or Advanced
        - **Required Tools/Materials**: List of necessary items
        - **Prerequisites**: Knowledge, access, or preparation needed
        - **Assumptions**: What the user should already know or have

        ## 📋 Preparation Steps
        - **Setup Tasks**: Initial configuration and preparation
        - **Safety Considerations**: Important warnings or precautions
        - **Backup Procedures**: Data backup or rollback plans if applicable
        - **Testing Environment**: Where to perform the steps

        ## 📝 Step-by-Step Instructions

        ### Step 1: [Clear, Action-Oriented Title]
        - **Objective**: What this step accomplishes
        - **Actions**: Specific, sequential sub-actions
        - **Expected Results**: What should happen after completion
        - **Time Estimate**: How long this step should take
        - **Visual Cues**: What to look for or expect

        [Continue with numbered steps as needed]

        ## 🔍 Verification & Testing
        - **Success Criteria**: How to know if steps were completed correctly
        - **Testing Procedures**: How to verify functionality
        - **Validation Steps**: Quality assurance checks
        - **Common Issues**: What to watch for

        ## 🚨 Troubleshooting & Common Issues
        - **Error Prevention**: How to avoid common mistakes
        - **Problem Diagnosis**: How to identify issues
        - **Solution Steps**: Specific fixes for common problems
        - **When to Seek Help**: When additional support is needed

        ## 🎉 Completion & Next Steps
        - **Final Verification**: Confirm successful completion
        - **Cleanup Tasks**: Any post-completion actions
        - **Documentation**: What to record or save
        - **Next Steps**: What to do after completion
        - **Additional Resources**: Where to learn more

        INSTRUCTION PRINCIPLES:
        - **Clarity**: Use simple, direct language
        - **Specificity**: Be precise about actions and expectations
        - **Sequence**: Logical, dependency-aware ordering
        - **Safety**: Include warnings for potentially destructive actions
        - **Flexibility**: Account for different scenarios or environments
        - **Progress**: Show clear progress through the process
        - **Recovery**: Provide ways to undo actions if needed

        FORMATTING ENHANCEMENTS:
        - Use clear, numbered steps with descriptive titles
        - Include sub-bullets for detailed actions within steps
        - Add visual indicators: ✅ (completed), 🔄 (in progress), ⚠️ (warning), 💡 (tip)
        - Use bold for important terms, file names, and UI elements
        - Add time estimates and difficulty indicators
        - Include screenshots descriptions or visual references if helpful
        - Use consistent formatting and terminology throughout
        - Add cross-references between related steps

        Output ONLY the formatted step-by-step guide with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Q&A Format - Comprehensive question and answer structure
                ReformatPrompt(
                    id = "qa_format",
                    name = "Q&A Format",
                    description = "Structure content as comprehensive questions and detailed answers with context",
                    promptText = """
                        Transform the following content into a comprehensive Q&A format. Analyze the content to identify key topics, then structure information as logical questions with detailed, helpful answers.

                        CONTENT ANALYSIS:
                        1. **Identify Topics**: What are the main subjects and concepts?
                        2. **Determine Questions**: What would readers naturally want to know?
                        3. **Assess Depth**: How much detail is appropriate for each answer?
                        4. **Find Relationships**: How do different pieces of information connect?
                        5. **Prioritize Information**: What is most important for readers to understand?

                        Q&A ORGANIZATION:

                        ## 🔍 Frequently Asked Questions
                        - **Common Questions**: Most likely reader questions
                        - **Clarifying Questions**: Questions that provide important context
        - **Detailed Explanations**: Complex topics requiring thorough explanation

        ## 📚 Core Concepts & Fundamentals
        - **Basic Understanding**: Foundational knowledge and definitions
        - **Key Principles**: Important concepts and frameworks
        - **Essential Knowledge**: Must-know information

        ## 💡 Practical Applications
        - **How-To Questions**: Specific procedures and processes
        - **Best Practices**: Recommended approaches and methods
        - **Implementation**: Real-world application guidance

        ## 🔧 Technical Details & Specifications
        - **Technical Specifications**: Detailed technical information
        - **Requirements & Constraints**: Important limitations and requirements
        - **Technical Troubleshooting**: Problem-solving guidance

        ## 🎯 Advanced Topics & Special Cases
        - **Complex Scenarios**: Advanced use cases and situations
        - **Edge Cases**: Unusual or exceptional circumstances
        - **Integration Points**: How different elements work together

        QUESTION & ANSWER FORMATTING:
        **Q: [Clear, specific question that addresses a key point]**

        **A:** [Comprehensive answer that includes:]
        - Direct answer to the question
        - Relevant context and background
        - Important details and considerations
        - Examples or illustrations where helpful
        - Related information or connections
        - Next steps or additional actions if applicable

        ENHANCEMENT FEATURES:
        - **Cross-references**: Link related questions and answers
        - **Progressive Disclosure**: Start with basics, then provide advanced details
        - **Examples**: Include concrete examples where they add clarity
        - **Context**: Provide necessary background information
        - **Action Items**: Include actionable next steps when relevant
        - **Visual Elements**: Use formatting to highlight key points
        - **Priority Indicators**: Mark essential vs. supplementary information

        QUALITY ASSURANCE:
        - Ensure all original information is captured and represented
        - Create logical, intuitive question groupings
        - Provide comprehensive yet accessible answers
        - Maintain professional, helpful tone throughout
        - Anticipate reader needs and questions
        - Organize from basic to advanced understanding
        - Include all important details without overwhelming the reader

        Output ONLY the formatted Q&A content with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Pros & Cons Analysis - Balanced evaluation framework
                ReformatPrompt(
                    id = "pros_cons",
                    name = "Pros & Cons Analysis",
                    description = "Comprehensive analysis of advantages, disadvantages, opportunities, and risks",
                    promptText = """
                        Perform a comprehensive pros and cons analysis of the following content. Extract and evaluate all positive and negative aspects, benefits and drawbacks, opportunities and risks.

                        ANALYSIS FRAMEWORK:

                        ## 📊 Overview & Context
                        - **Subject of Analysis**: What is being evaluated?
        - **Analysis Criteria**: What factors are most important?
        - **Evaluation Scope**: What aspects are included/excluded?
        - **Time Horizon**: Short-term vs. long-term considerations

        ## ✅ STRENGTHS & ADVANTAGES
        - **Core Benefits**: Primary advantages and positive outcomes
        - **Strategic Advantages**: Competitive or positioning benefits
        - **Efficiency Gains**: Time, cost, or resource savings
        - **Quality Improvements**: Enhanced outcomes or capabilities
        - **Innovation Benefits**: New opportunities or capabilities

        ## ⚠️ WEAKNESSES & DISADVANTAGES
        - **Core Drawbacks**: Primary disadvantages and negative outcomes
        - **Resource Requirements**: Additional costs, time, or effort needed
        - **Implementation Challenges**: Difficulty or complexity factors
        - **Risk Factors**: Potential problems or obstacles
        - **Limitation Factors**: Constraints or restrictions

        ## 🎯 OPPORTUNITIES & BENEFITS
        - **Growth Opportunities**: Potential for expansion or improvement
        - **Strategic Advantages**: Long-term positioning benefits
        - **Market Opportunities**: Business or market advantages
        - **Innovation Potential**: New capabilities or breakthroughs
        - **Synergy Effects**: Benefits from combining with other factors

        ## 🚧 THREATS & RISKS
        - **High-Risk Factors**: Significant potential problems
        - **External Threats**: Market, competitive, or environmental risks
        - **Internal Challenges**: Organizational or operational risks
        - **Implementation Risks**: Execution or adoption challenges
        - **Mitigation Needs**: Actions required to address risks

        ## 📈 IMPACT ANALYSIS
        - **Short-term Impact**: Immediate effects and outcomes
        - **Long-term Impact**: Future implications and consequences
        - **Stakeholder Impact**: Effects on different groups or individuals
        - **Financial Impact**: Cost and revenue implications
        - **Operational Impact**: Effects on processes and operations

        ## 🔄 ALTERNATIVES & TRADE-OFFS
        - **Alternative Approaches**: Other ways to achieve similar outcomes
        - **Trade-off Considerations**: What would be gained or lost
        - **Hybrid Options**: Combination approaches that might work better
        - **Contingency Plans**: Backup approaches if primary path fails

        ## 💡 RECOMMENDATIONS & DECISIONS
        - **Overall Assessment**: Net positive, negative, or neutral
        - **Recommended Actions**: Suggested next steps or decisions
        - **Implementation Guidance**: How to proceed if approved
        - **Monitoring Needs**: What should be tracked going forward
        - **Success Metrics**: How to measure effectiveness

        ANALYSIS PRINCIPLES:
        - **Balance**: Present both positive and negative aspects fairly
        - **Evidence**: Base analysis on facts rather than opinions
        - **Context**: Consider the specific situation and constraints
        - **Priorities**: Focus on the most important factors
        - **Objectivity**: Maintain neutral, analytical perspective
        - **Completeness**: Cover all significant aspects and implications
        - **Clarity**: Make complex analysis easy to understand

        FORMATTING ENHANCEMENTS:
        - Use clear visual hierarchy with headings and subheadings
        - Add weight indicators: 🔥 (critical), 📍 (important), 📝 (minor)
        - Include probability estimates for risks and opportunities
        - Use color coding or icons for positive/negative/neutral items
        - Group related items under thematic categories
        - Add cross-references between related points
        - Include implementation priority rankings
        - Use consistent formatting and terminology

        Output ONLY the comprehensive pros and cons analysis with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Timeline - Chronological organization with context
                ReformatPrompt(
                    id = "timeline_format",
                    name = "Timeline",
                    description = "Chronological timeline with events, milestones, dependencies, and context",
                    promptText = """
                        Organize the following content into a comprehensive chronological timeline. Extract dates, events, sequences, and temporal relationships to create a clear chronological narrative.

                        TIMELINE ANALYSIS:
                        1. **Identify Time Elements**: Extract all dates, times, durations, and sequences
                        2. **Establish Chronology**: Determine the correct time order of events
                        3. **Find Dependencies**: Identify events that depend on or lead to others
                        4. **Assess Duration**: Note time spans, milestones, and critical paths
                        5. **Determine Context**: Understand what each event represents and why it matters

                        TIMELINE STRUCTURE:

                        ## 📅 Timeline Overview
                        - **Time Period**: Overall duration covered
        - **Key Phases**: Major chronological sections or stages
        - **Critical Path**: Most important sequence of events
        - **Major Milestones**: Key turning points or achievements

        ## 📊 Chronological Events & Milestones

        ### [Date/Time] - [Event Title]
        - **Description**: Detailed explanation of what happened
        - **Significance**: Why this event was important
        - **Participants**: Who was involved
        - **Outcomes**: Results or consequences
        - **Next Steps**: What happened as a result

        [Continue with subsequent dated events]

        ## 🔄 Process Flow & Dependencies
        - **Sequential Steps**: Events that must happen in order
        - **Parallel Activities**: Events that can happen simultaneously
        - **Dependencies**: Events that require others to complete first
        - **Critical Dependencies**: Show-stopping prerequisites

        ## 🎯 Key Milestones & Achievements
        - **Major Accomplishments**: Significant achievements or completions
        - **Decision Points**: Important choices or turning points
        - **Project Milestones**: Key project phases or completions
        - **Success Metrics**: Measurable achievements or outcomes

        ## ⏰ Timeline Analysis
        - **Duration Analysis**: How long different phases took
        - **Delay Factors**: Events or issues that caused delays
        - **Efficiency Insights**: Where time was well-spent or wasted
        - **Optimization Opportunities**: Ways to improve future timelines

        ## 📈 Future Timeline & Projections
        - **Upcoming Milestones**: Future important dates and events
        - **Projected Timeline**: Expected future schedule
        - **Contingency Plans**: Backup timelines if things go off-track
        - **Risk Factors**: Events that could affect future timeline

        TIMELINE FORMATTING:
        - **Date Format**: Use consistent, clear date/time formatting
        - **Visual Timeline**: Create visual flow with arrows or progression indicators
        - **Status Indicators**: ✅ (completed), 🔄 (in progress), ⏳ (upcoming), 🚨 (overdue)
        - **Priority Levels**: 🔥 (critical), 📍 (important), 📝 (minor)
        - **Duration Indicators**: Show time spans and elapsed time where relevant
        - **Connection Lines**: Show relationships between events
        - **Progress Markers**: Indicate completion percentage or status

        ENHANCEMENT FEATURES:
        - Add context and background for each major event
        - Include relevant metrics, outcomes, or impacts
        - Note decision points and their rationales
        - Highlight critical path items and dependencies
        - Add visual elements to show timeline flow
        - Include buffer time and contingency planning
        - Show parallel vs. sequential activities
        - Add notes about lessons learned or best practices

        QUALITY ASSURANCE:
        - Verify chronological accuracy of all events
        - Ensure all original information is preserved
        - Create logical, easy-to-follow progression
        - Include sufficient context for each event
        - Balance comprehensive coverage with readability

        Output ONLY the formatted chronological timeline with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                ),
                
                // Bullet Point Summary - Structured, scannable overview
                ReformatPrompt(
                    id = "bullet_summary",
                    name = "Bullet Point Summary",
                    description = "Organized bullet point summary with hierarchy, emphasis, and clear structure",
                    promptText = """
                        Transform the following content into a comprehensive, well-organized bullet point summary. Create a logical hierarchy that makes information easy to scan, understand, and act upon.

                        CONTENT ANALYSIS:
                        1. **Identify Main Themes**: What are the primary topics and concepts?
                        2. **Determine Hierarchy**: How do ideas relate and organize?
                        3. **Find Key Points**: What are the most important details?
                        4. **Assess Relationships**: How do different pieces connect?
                        5. **Prioritize Information**: What needs most emphasis?

                        BULLET POINT HIERARCHY:

                        ## 🎯 MAIN TOPIC OR THEME
                        - **Primary Point**: Most important information or conclusion
          - Supporting detail or explanation
          - Additional context or example
          - Related consideration or implication
        - **Secondary Point**: Next most important information
          - Supporting details and context
          - Specific examples or data points
        - **Additional Points**: Other relevant information
          - Sub-details as needed
          - Related information or considerations

        ## 📋 NEXT MAJOR TOPIC
        - **Key Concept**: Main idea or principle
          - Explanation and context
          - Important details or specifications
        - **Supporting Information**: Related facts or details
          - Specific examples or instances
          - Data points or metrics if applicable
        - **Implementation Notes**: Practical considerations
          - Action items or next steps
          - Requirements or prerequisites

        ## 💡 ADDITIONAL TOPICS AS NEEDED
        - Continue with logical topic organization
        - Maintain consistent hierarchy and formatting
        - Ensure comprehensive coverage of content

                        FORMATTING PRINCIPLES:
                        - **Hierarchy**: Use main bullets, sub-bullets, and sub-sub-bullets logically
                        - **Consistency**: Maintain consistent formatting and indentation
                        - **Clarity**: Use clear, concise language in each bullet
                        - **Completeness**: Ensure all important information is captured
                        - **Logic**: Organize bullets in logical reading order
                        - **Emphasis**: Use bold for key terms and important information

                        ENHANCEMENT FEATURES:
                        - **Visual Indicators**: Add icons or symbols for different types of information
                        - **Priority Markers**: Use 🔥 (critical), 📍 (important), 📝 (informational)
                        - **Status Indicators**: Add ✅ (completed), 🔄 (in progress), ⏳ (pending)
                        - **Category Labels**: Group related bullets under thematic headings
                        - **Cross-References**: Link related items across sections
                        - **Action Items**: Highlight actionable information
                        - **Key Takeaways**: Emphasize important conclusions or insights

                        BULLET POINT BEST PRACTICES:
                        - Keep each bullet focused on a single concept
                        - Use parallel structure across related bullets
                        - Start with the most important information
                        - Use consistent tense and voice
                        - Avoid overly long or complex bullets
                        - Break complex ideas into multiple bullets
                        - Use transitions between major sections
                        - Include sufficient context for understanding
                        - End with actionable next steps when applicable

                        QUALITY ASSURANCE:
                        - Verify all original content is represented
                        - Ensure logical flow and organization
                        - Check for consistent formatting and style
                        - Confirm readability and scannability
                        - Validate that hierarchy makes sense
                        - Ensure comprehensive yet concise presentation

                        Output ONLY the formatted bullet point summary with no preamble, explanations, or additional commentary.
                    """.trimIndent(),
                    isDefault = true
                )
            )
        }

        /**
         * Creates a new user-created prompt with the specified details
         */
        fun createUserPrompt(name: String, description: String, promptText: String): ReformatPrompt {
            return ReformatPrompt(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                promptText = promptText.trim(),
                isDefault = false
            )
        }
    }
}